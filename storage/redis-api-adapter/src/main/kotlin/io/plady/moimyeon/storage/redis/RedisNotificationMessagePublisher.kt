package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.core.notification.NotificationMessagePublisher
import io.plady.moimyeon.core.notification.OutgoingNotification
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
    override fun publish(notifications: List<OutgoingNotification>) {
        val entries = notifications.flatMap { notification ->
            notification.policy.channels.map { channel ->
                listOf(notification.eventId.toString(), notification.eventType.name, channel.name, notification.payload)
            }
        }
        if (entries.isEmpty()) return

        val publishedCount = redisTemplate.execute(
            PUBLISH_CHANNEL_MESSAGES_SCRIPT,
            listOf(properties.streamKey),
            *entries.flatten().toTypedArray(),
        )
        check(publishedCount == entries.size.toLong()) {
            "알림의 채널 메시지를 모두 저장하지 못했습니다. eventId=${notifications.first().eventId}"
        }
    }

    private companion object {
        // 스크립트는 중간 XADD 가 실패해도 앞선 쓰기를 되돌리지 않는다. 같은 Stream 에 대한 XADD 가 중간에만 실패하는 것은
        // 메모리 한도에 걸리는 경우뿐이라, 그때 재시도로 생기는 중복 발송은 받아들인다.
        val PUBLISH_CHANNEL_MESSAGES_SCRIPT = DefaultRedisScript(
            """
            local published = 0
            for index = 1, #ARGV, 4 do
                redis.call(
                    'XADD', KEYS[1], '*',
                    'eventId', ARGV[index],
                    'eventType', ARGV[index + 1],
                    'channel', ARGV[index + 2],
                    'payload', ARGV[index + 3]
                )
                published = published + 1
            end
            return published
            """.trimIndent(),
            Long::class.java,
        )
    }
}
