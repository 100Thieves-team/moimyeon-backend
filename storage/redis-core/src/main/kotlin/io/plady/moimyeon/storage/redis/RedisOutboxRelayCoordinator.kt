package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.core.notification.outbox.OutboxRelayCoordinator
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.util.UUID

@Profile("!test")
@Component
internal class RedisOutboxRelayCoordinator(
    private val redisTemplate: StringRedisTemplate,
    private val properties: RedisOutboxRelayProperties,
) : OutboxRelayCoordinator {
    override fun relayPendingIfAvailable(relay: () -> Unit): Boolean {
        val ownerToken = UUID.randomUUID().toString()
        val acquired = redisTemplate.opsForValue().setIfAbsent(
            properties.lockKey,
            ownerToken,
            properties.lockDuration,
        ) == true
        if (!acquired) {
            return false
        }

        try {
            relay()
            return true
        } finally {
            redisTemplate.execute(RELEASE_SCRIPT, listOf(properties.lockKey), ownerToken)
        }
    }

    private companion object {
        val RELEASE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}
