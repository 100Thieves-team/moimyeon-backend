package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.core.notification.outbox.NotificationMessagePublisher
import io.plady.moimyeon.core.notification.outbox.RelayMessage
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

@Profile("!test")
@Component
internal class RedisNotificationMessagePublisher(
    private val redisTemplate: StringRedisTemplate,
    private val properties: RedisNotificationStreamProperties,
) : NotificationMessagePublisher {
    override fun publish(message: RelayMessage) {
        val channels = message.eventType.notificationChannels
        val arguments = buildList {
            add(message.eventId.toString())
            add(message.eventType.name)
            add(message.payload)
            addAll(channels.map { it.name })
        }
        val publishedCount = redisTemplate.execute(
            PUBLISH_CHANNEL_MESSAGES_SCRIPT,
            listOf(properties.streamKey),
            *arguments.toTypedArray(),
        )
        check(publishedCount == channels.size.toLong()) {
            "이벤트 정책의 채널 메시지를 모두 저장하지 못했습니다. eventId=${message.eventId}"
        }
    }

    private companion object {
        val PUBLISH_CHANNEL_MESSAGES_SCRIPT = DefaultRedisScript(
            """
            local published = 0
            for index = 4, #ARGV do
                redis.call(
                    'XADD', KEYS[1], '*',
                    'eventId', ARGV[1],
                    'eventType', ARGV[2],
                    'channel', ARGV[index],
                    'payload', ARGV[3]
                )
                published = published + 1
            end
            return published
            """.trimIndent(),
            Long::class.java,
        )
    }
}
