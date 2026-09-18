package io.plady.moimyeon.support.logging

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.SentryLogEvent
import io.sentry.SentryLogEventAttributeValue
import io.sentry.protocol.Mechanism
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace

class SentryPrivacyFilter(
    private val serviceName: String,
    private val environment: String,
    private val release: String,
) {
    fun event(source: SentryEvent): SentryEvent = SentryEvent(source.timestamp).apply {
        eventId = source.eventId
        level = source.level
        logger = source.logger
        platform = "java"
        sdk = source.sdk
        environment = this@SentryPrivacyFilter.environment
        release = this@SentryPrivacyFilter.release
        setTag("service.name", serviceName)
        // Rebuild from an allowlist: even exception messages and MDC may contain credentials,
        // SQL parameters, email addresses or notification payloads. Keep code locations only.
        message = Message().apply { formatted = "Application error (message redacted)" }
        exceptions = source.exceptions?.map(::exception)
        breadcrumbs = source.breadcrumbs?.mapNotNull(::breadcrumb)
    }

    fun breadcrumb(source: Breadcrumb): Breadcrumb? {
        val category = source.category?.takeIf { it.startsWith("io.plady.moimyeon.") } ?: return null
        return Breadcrumb(source.timestamp).apply {
            this.category = category
            type = "log"
            level = source.level
            message = "Application log (message redacted)"
        }
    }

    fun log(source: SentryLogEvent): SentryLogEvent? {
        // No arbitrary application or vendor logs leave the process. New safe event codes need
        // explicit review here; enabling SENTRY_LOGS_ENABLED alone does not export full stdout.
        if (source.body !in SAFE_LOG_EVENTS) return null
        return SentryLogEvent(source.traceId, source.timestamp, source.body, source.level).apply {
            setAttribute("service.name", SentryLogEventAttributeValue("string", serviceName))
            setAttribute("deployment.environment.name", SentryLogEventAttributeValue("string", environment))
            setAttribute("service.version", SentryLogEventAttributeValue("string", release))
        }
    }

    private fun exception(source: SentryException): SentryException = SentryException().apply {
        type = source.type
        module = source.module
        mechanism = source.mechanism?.let {
            Mechanism().apply {
                type = it.type
                isHandled = it.isHandled
            }
        }
        stacktrace = source.stacktrace?.let { trace ->
            SentryStackTrace().apply {
                frames = trace.frames?.map { frame ->
                    SentryStackFrame().apply {
                        filename = frame.filename
                        module = frame.module
                        function = frame.function
                        lineno = frame.lineno
                        isInApp = frame.isInApp
                    }
                }
            }
        }
    }

    companion object {
        const val SERVICE_READY = "service.ready"
        private val SAFE_LOG_EVENTS = setOf(SERVICE_READY)
    }
}
