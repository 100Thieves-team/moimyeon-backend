package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.core.notification.outbox.NotificationMessagePublisher
import io.plady.moimyeon.core.notification.outbox.RelayMessage
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Profile("!test")
@Component
internal class RedisNotificationMessagePublisher(
    private val redisTemplate: StringRedisTemplate,
    private val properties: RedisNotificationStreamProperties,
) : NotificationMessagePublisher {
    override fun publish(message: RelayMessage) {
        val record = StreamRecords.string(
            mapOf(
                "eventId" to message.eventId.toString(),
                "eventType" to message.eventType.name,
                "payload" to message.payload,
            ),
        ).withStreamKey(properties.streamKey)

        redisTemplate.opsForStream<String, String>().add(record)
    }
}
