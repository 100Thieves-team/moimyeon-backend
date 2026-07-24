package io.plady.moimyeon.core.domain.session

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "security.session")
data class SessionProperties(
    val ttlSeconds: Long,
)
