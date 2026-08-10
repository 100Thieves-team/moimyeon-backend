package io.plady.moimyeon.storage.redis

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile

@ConfigurationProperties("storage.redis.notification")
@Profile("!test")
data class RedisNotificationStreamProperties(
    val streamKey: String,
    val deadLetterStreamKey: String = "$streamKey-dead-letter",
)
