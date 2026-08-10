package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.core.enums.EventType
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.dao.DataAccessException
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Profile("!test")
@ConditionalOnProperty(
    prefix = "notification.worker.consumer",
    name = ["enabled"],
    havingValue = "true",
)
@Component
class RedisNotificationStreamConsumer(
    private val redisTemplate: StringRedisTemplate,
    private val properties: RedisNotificationStreamConsumerProperties,
    private val streamProperties: RedisNotificationStreamProperties,
) : NotificationStreamConsumer {
    private val groupMonitor = Any()

    @Volatile
    private var groupReady: Boolean = false

    override fun consumeNew(handler: (NotificationStreamMessage) -> Unit): Int {
        ensureConsumerGroup()
        val records = streamOperations.read(
            consumer,
            StreamReadOptions.empty().count(properties.batchSize.toLong()),
            StreamOffset.create(streamProperties.streamKey, ReadOffset.lastConsumed()),
        )
        return process(records, handler)
    }

    override fun recoverPending(handler: (NotificationStreamMessage) -> Unit): Int {
        ensureConsumerGroup()
        val pending = streamOperations.pending(
            streamProperties.streamKey,
            properties.groupName,
            Range.unbounded<String>(),
            properties.batchSize.toLong(),
            properties.pendingMinIdle,
        )
        if (pending.isEmpty) {
            return 0
        }

        val records = streamOperations.claim(
            streamProperties.streamKey,
            properties.groupName,
            properties.consumerName,
            properties.pendingMinIdle,
            *pending.map { it.id }.toList().toTypedArray(),
        )
        return process(records, handler)
    }

    private fun process(
        records: List<MapRecord<String, String, String>>,
        handler: (NotificationStreamMessage) -> Unit,
    ): Int {
        var acknowledged = 0
        records.forEach { record ->
            try {
                handler(record.toMessage())
                check(streamOperations.acknowledge(properties.groupName, record) == 1L) {
                    "Redis Stream 메시지를 ACK하지 못했습니다. recordId=${record.id.value}"
                }
                acknowledged++
            } catch (exception: Exception) {
                log.error(
                    "Redis Stream 메시지 처리에 실패해 Pending으로 유지합니다. recordId={}",
                    record.id.value,
                    exception,
                )
            }
        }
        return acknowledged
    }

    private fun ensureConsumerGroup() {
        if (groupReady) {
            return
        }
        synchronized(groupMonitor) {
            if (groupReady) {
                return
            }
            try {
                streamOperations.createGroup(
                    streamProperties.streamKey,
                    ReadOffset.from("0-0"),
                    properties.groupName,
                )
            } catch (exception: DataAccessException) {
                if (!exception.hasBusyGroupCause()) {
                    throw exception
                }
            }
            groupReady = true
        }
    }

    private fun MapRecord<String, String, String>.toMessage(): NotificationStreamMessage {
        val eventId = value[EVENT_ID]?.let(::parseEventId)
            ?: throw IllegalArgumentException("Redis Stream 메시지의 eventId가 올바르지 않습니다.")
        val eventType = value[EVENT_TYPE]?.let(EventType::valueOf)
            ?: throw IllegalArgumentException("Redis Stream 메시지의 eventType이 없습니다.")
        val payload = value[PAYLOAD]
            ?: throw IllegalArgumentException("Redis Stream 메시지의 payload가 없습니다.")
        return NotificationStreamMessage(
            eventId = eventId,
            eventType = eventType,
            payload = payload,
        )
    }

    private fun parseEventId(value: String): UUID? = runCatching { UUID.fromString(value) }
        .getOrNull()

    private fun Throwable.hasBusyGroupCause(): Boolean = generateSequence(this) { it.cause }
        .any { it.message?.contains("BUSYGROUP") == true }

    private val streamOperations
        get() = redisTemplate.opsForStream<String, String>()

    private val consumer
        get() = Consumer.from(properties.groupName, properties.consumerName)

    private companion object {
        const val EVENT_ID = "eventId"
        const val EVENT_TYPE = "eventType"
        const val PAYLOAD = "payload"
        val log = LoggerFactory.getLogger(RedisNotificationStreamConsumer::class.java)
    }
}
