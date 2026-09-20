package io.plady.moimyeon.support.logging

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.Environment
import java.time.Duration

@ConfigurationProperties("moimyeon.logging.policy")
data class LoggingProperties(
    val slowRequestThreshold: Duration = Duration.ofSeconds(1),
    val excludedPaths: List<String> = listOf("/actuator/health", "/health", "/favicon.ico"),
    val maxStackFrames: Int = 30,
    val maxExceptionDepth: Int = 5,
) {
    init {
        require(slowRequestThreshold >= Duration.ofMillis(1) && slowRequestThreshold <= Duration.ofMinutes(5)) { "Invalid slow request threshold" }
        require(maxStackFrames in 0..30) { "Invalid stack frame limit" }
        require(maxExceptionDepth in 1..5) { "Invalid exception depth limit" }
        require(excludedPaths.size <= 32 && excludedPaths.all { it.length <= 256 && EXCLUDED_PATH.matches(it) }) { "Invalid excluded paths" }
    }

    companion object {
        private val EXCLUDED_PATH = Regex("(?:/[A-Za-z0-9._-]+)+")

        fun from(environment: Environment): LoggingProperties = Binder.get(environment)
            .bind("moimyeon.logging.policy", LoggingProperties::class.java)
            .orElseGet { LoggingProperties() }
    }
}
