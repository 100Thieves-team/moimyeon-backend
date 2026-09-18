package io.plady.moimyeon.support.logging

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class LoggingEnvironmentPostProcessor :
    EnvironmentPostProcessor,
    Ordered {
    override fun getOrder(): Int = ConfigDataEnvironmentPostProcessor.ORDER + 1

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val profiles = environment.activeProfiles.toSet()
        var validationError = ""
        val selected = try {
            LoggingEnvironment.from(profiles)
        } catch (failure: IllegalArgumentException) {
            validationError = requireNotNull(failure.message)
            LoggingEnvironment.LIVE
        }
        val deployment = environment.getProperty("DEPLOYMENT_ENVIRONMENT")
        if (validationError.isEmpty() && deployment != null && deployment != selected.profile) {
            validationError = "Deployment environment does not match logging environment"
        }
        val settings = try {
            LoggingProperties.from(environment)
        } catch (_: Exception) {
            if (validationError.isEmpty()) validationError = "Invalid logging properties"
            LoggingProperties()
        }

        val loggingProfile = when {
            validationError.isNotEmpty() -> "bootstrap"
            selected == LoggingEnvironment.DEV && "perf" in profiles -> "dev-perf"
            else -> selected.profile
        }
        val properties = mutableMapOf<String, Any>(
            "logging.config" to "classpath:logback/logback-$loggingProfile.xml",
            "moimyeon.logging.profile" to loggingProfile,
            "moimyeon.logging.validation-error" to validationError,
            "moimyeon.logging.environment" to if (validationError.isEmpty()) selected.profile else "invalid",
            "moimyeon.logging.policy.slow-request-threshold" to settings.slowRequestThreshold.toString(),
            "moimyeon.logging.policy.excluded-paths" to settings.excludedPaths,
            "moimyeon.logging.policy.max-stack-frames" to settings.maxStackFrames,
            "moimyeon.logging.policy.max-exception-depth" to settings.maxExceptionDepth,
            // Hibernate show_sql bypasses Logback, including its output privacy policy.
            "spring.jpa.show-sql" to false,
            "spring.jpa.properties.hibernate.show_sql" to false,
            "logging.level.org.hibernate.SQL" to "OFF",
            "logging.level.org.hibernate.orm.jdbc.bind" to "OFF",
            "logging.level.org.hibernate.orm.jdbc.extract" to "OFF",
            "logging.level.org.apache.http.wire" to "OFF",
            "logging.level.org.apache.hc.client5.http.wire" to "OFF",
        )
        if (selected in setOf(LoggingEnvironment.LOCAL, LoggingEnvironment.LOCAL_DEV, LoggingEnvironment.TEST) || validationError.isNotEmpty()) {
            properties.putAll(
                mapOf(
                    "sentry.enabled" to false,
                    "sentry.logging.enabled" to false,
                    "sentry.logs.enabled" to false,
                    "management.otlp.metrics.export.enabled" to false,
                    "management.tracing.export.otlp.enabled" to false,
                    "management.logging.export.otlp.enabled" to false,
                ),
            )
        }
        environment.propertySources.addFirst(MapPropertySource("moimyeonLoggingPolicy", properties))
    }
}
