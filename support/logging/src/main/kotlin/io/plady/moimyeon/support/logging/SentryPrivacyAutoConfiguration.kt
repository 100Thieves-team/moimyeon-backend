package io.plady.moimyeon.support.logging

import io.sentry.SentryOptions
import io.sentry.spring.boot4.SentryAutoConfiguration
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment

@AutoConfiguration(before = [SentryAutoConfiguration::class])
class SentryPrivacyAutoConfiguration {
    @Bean
    fun sentryPrivacyFilter(environment: Environment): SentryPrivacyFilter = SentryPrivacyFilter(
        serviceName = environment.getProperty("OTEL_SERVICE_NAME")
            ?: environment.getProperty("spring.application.name", "unknown-service"),
        environment = environment.getProperty("DEPLOYMENT_ENVIRONMENT")
            ?: environment.getProperty("spring.profiles.active", "local"),
        release = environment.getProperty("APP_RELEASE", "local"),
    )

    @Bean
    fun privacyBeforeSend(filter: SentryPrivacyFilter): SentryOptions.BeforeSendCallback = SentryOptions.BeforeSendCallback { event, _ -> filter.event(event) }

    @Bean
    fun privacyBeforeBreadcrumb(filter: SentryPrivacyFilter): SentryOptions.BeforeBreadcrumbCallback = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ -> filter.breadcrumb(breadcrumb) }

    @Bean
    fun privacyBeforeSendLog(filter: SentryPrivacyFilter): SentryOptions.Logs.BeforeSendLogCallback = SentryOptions.Logs.BeforeSendLogCallback(filter::log)

    @EventListener(ApplicationReadyEvent::class)
    fun ready() {
        LoggerFactory.getLogger(SentryPrivacyAutoConfiguration::class.java)
            .info(SentryPrivacyFilter.SERVICE_READY)
    }
}
