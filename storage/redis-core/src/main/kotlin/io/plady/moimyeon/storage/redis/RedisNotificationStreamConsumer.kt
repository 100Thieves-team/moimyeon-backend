package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
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
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Instant
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
    private val metrics: NotificationStreamMetrics,
) : NotificationStreamConsumer {
    private val groupMonitor = Any()
    private val retryPolicy = NotificationRetryPolicy(
        initialDelay = properties.pendingMinIdle,
        maxDelay = properties.retryMaxDelay,
        maxAttempts = properties.maxAttempts,
    )

    @Volatile
    private var groupReady: Boolean = false

    override fun consumeNew(handler: (NotificationStreamMessage) -> NotificationStreamHandlingResult): Int {
        ensureConsumerGroup()
        val records = streamOperations.read(
            consumer,
            StreamReadOptions.empty().count(properties.batchSize.toLong()),
            StreamOffset.create(streamProperties.streamKey, ReadOffset.lastConsumed()),
        )
        val acknowledged = process(records, emptyMap(), handler)
        refreshPendingMetric()
        return acknowledged
    }

    override fun recoverPending(handler: (NotificationStreamMessage) -> NotificationStreamHandlingResult): Int {
        ensureConsumerGroup()
        val pending = streamOperations.pending(
            streamProperties.streamKey,
            properties.groupName,
            Range.unbounded<String>(),
            properties.pendingScanSize.toLong(),
            properties.pendingMinIdle,
        )
        if (pending.isEmpty) {
            return 0
        }

        val retryablePending = pending.asSequence()
            .filter {
                it.elapsedTimeSinceLastDelivery >= retryPolicy.delayBeforeAttempt(it.totalDeliveryCount + 1)
            }
            .take(properties.batchSize)
            .toList()
        if (retryablePending.isEmpty()) {
            return 0
        }

        val records = streamOperations.claim(
            streamProperties.streamKey,
            properties.groupName,
            properties.consumerName,
            properties.pendingMinIdle,
            *retryablePending.map { it.id }.toTypedArray(),
        )
        val attemptsByRecordId = retryablePending.associate {
            it.idAsString to it.totalDeliveryCount + 1
        }
        return process(records, attemptsByRecordId, handler)
    }

    private fun process(
        records: List<MapRecord<String, String, String>>,
        attemptsByRecordId: Map<String, Long>,
        handler: (NotificationStreamMessage) -> NotificationStreamHandlingResult,
    ): Int {
        var acknowledged = 0
        records.forEach { record ->
            val attemptCount = attemptsByRecordId[record.id.value] ?: FIRST_ATTEMPT
            try {
                if (!retryPolicy.hasAttemptsRemaining(attemptCount - 1)) {
                    deadLetter(
                        record = record,
                        result = NotificationStreamHandlingResult.retryableFailure(
                            failureType = RETRY_ATTEMPTS_EXHAUSTED,
                            failureMessage = "알림 메시지 처리 시도 횟수를 초과했습니다.",
                        ),
                        attemptCount = attemptCount - 1,
                        exhausted = true,
                    )
                    metrics.deadLetter(record.value[EVENT_TYPE], record.value[CHANNEL])
                    acknowledged++
                    return@forEach
                }

                val result = handle(record, handler)
                when {
                    result.isSuccess -> {
                        acknowledge(record)
                        metrics.success(record.value[EVENT_TYPE], record.value[CHANNEL])
                        acknowledged++
                    }
                    result.isPermanentFailure -> {
                        deadLetter(record, result, attemptCount)
                        metrics.deadLetter(record.value[EVENT_TYPE], record.value[CHANNEL])
                        acknowledged++
                    }
                    !retryPolicy.hasAttemptsRemaining(attemptCount) -> {
                        deadLetter(record, result, attemptCount, exhausted = true)
                        metrics.deadLetter(record.value[EVENT_TYPE], record.value[CHANNEL])
                        acknowledged++
                    }
                    else -> {
                        metrics.retry(record.value[EVENT_TYPE], record.value[CHANNEL])
                        log.warn(
                            "Redis Stream 메시지를 재시도할 수 있도록 Pending으로 유지합니다. recordId={}, attemptCount={}, failureType={}",
                            record.id.value,
                            attemptCount,
                            result.failureType,
                            result.cause,
                        )
                    }
                }
            } catch (exception: Exception) {
                metrics.retry(record.value[EVENT_TYPE], record.value[CHANNEL])
                log.error(
                    "Redis Stream 메시지 처리에 실패해 Pending으로 유지합니다. recordId={}",
                    record.id.value,
                    exception,
                )
            }
        }
        return acknowledged
    }

    private fun refreshPendingMetric() {
        try {
            metrics.pending(
                streamOperations.pending(
                    streamProperties.streamKey,
                    properties.groupName,
                ).totalPendingMessages,
            )
        } catch (exception: DataAccessException) {
            log.warn("Redis Stream Pending 메트릭을 갱신하지 못했습니다.", exception)
        }
    }

    private fun handle(
        record: MapRecord<String, String, String>,
        handler: (NotificationStreamMessage) -> NotificationStreamHandlingResult,
    ): NotificationStreamHandlingResult {
        val message = try {
            record.toMessage()
        } catch (exception: Exception) {
            return NotificationStreamHandlingResult.permanentFailure(
                failureType = INVALID_STREAM_MESSAGE,
                failureMessage = exception.message,
                cause = exception,
            )
        }
        return try {
            handler(message)
        } catch (exception: Exception) {
            NotificationStreamHandlingResult.retryableFailure(
                failureType = exception.javaClass.simpleName,
                failureMessage = exception.message,
                cause = exception,
            )
        }
    }

    private fun acknowledge(record: MapRecord<String, String, String>) {
        check(streamOperations.acknowledge(properties.groupName, record) == 1L) {
            "Redis Stream 메시지를 ACK하지 못했습니다. recordId=${record.id.value}"
        }
    }

    private fun deadLetter(
        record: MapRecord<String, String, String>,
        result: NotificationStreamHandlingResult,
        attemptCount: Long,
        exhausted: Boolean = false,
    ) {
        val failureType = if (exhausted) RETRY_ATTEMPTS_EXHAUSTED else checkNotNull(result.failureType)
        val archivedAndAcknowledged = redisTemplate.execute(
            DEAD_LETTER_AND_ACK_SCRIPT,
            listOf(streamProperties.streamKey, streamProperties.deadLetterStreamKey),
            properties.groupName,
            record.id.value,
            record.value[EVENT_ID].orEmpty(),
            record.value[EVENT_TYPE].orEmpty(),
            record.value[CHANNEL].orEmpty(),
            record.value[PAYLOAD].orEmpty(),
            failureType,
            result.failureType.orEmpty(),
            result.failureMessage.orEmpty().take(MAX_FAILURE_MESSAGE_LENGTH),
            attemptCount.toString(),
            Instant.now().toString(),
        )
        check(archivedAndAcknowledged == 1L) {
            "실패한 Redis Stream 메시지를 DLQ에 기록하고 ACK하지 못했습니다. recordId=${record.id.value}"
        }
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
        val channel = value[CHANNEL]?.let(NotificationChannel::valueOf)
            ?: throw IllegalArgumentException("Redis Stream 메시지의 channel이 없습니다.")
        val payload = value[PAYLOAD]
            ?: throw IllegalArgumentException("Redis Stream 메시지의 payload가 없습니다.")
        return NotificationStreamMessage(
            eventId = eventId,
            eventType = eventType,
            channel = channel,
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
        const val CHANNEL = "channel"
        const val PAYLOAD = "payload"
        const val FIRST_ATTEMPT = 1L
        const val MAX_FAILURE_MESSAGE_LENGTH = 1_000
        const val INVALID_STREAM_MESSAGE = "InvalidStreamMessage"
        const val RETRY_ATTEMPTS_EXHAUSTED = "RetryAttemptsExhausted"
        val DEAD_LETTER_AND_ACK_SCRIPT = DefaultRedisScript(
            """
            redis.call(
                'XADD', KEYS[2], '*',
                'sourceRecordId', ARGV[2],
                'eventId', ARGV[3],
                'eventType', ARGV[4],
                'channel', ARGV[5],
                'payload', ARGV[6],
                'failureType', ARGV[7],
                'originalFailureType', ARGV[8],
                'failureMessage', ARGV[9],
                'attemptCount', ARGV[10],
                'failedAt', ARGV[11]
            )
            return redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
            """.trimIndent(),
            Long::class.java,
        )
        val log = LoggerFactory.getLogger(RedisNotificationStreamConsumer::class.java)
    }
}
