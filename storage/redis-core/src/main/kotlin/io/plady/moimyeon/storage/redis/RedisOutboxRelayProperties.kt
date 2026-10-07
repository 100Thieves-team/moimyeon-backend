package io.plady.moimyeon.storage.redis

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile
import java.time.Duration

@ConfigurationProperties("storage.redis.notification.outbox-relay")
@Profile("!test")
data class RedisOutboxRelayProperties(
    val lockKey: String,
    val lockDuration: Duration,
)
