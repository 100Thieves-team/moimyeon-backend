package io.plady.moimyeon.support.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import java.time.Instant

class LogSanitizer(
    private val properties: LoggingProperties,
    service: String?,
    deployment: String?,
    release: String?,
) {
    private val service = identifier(service) ?: "unknown-service"
    private val deployment = identifier(deployment) ?: "unknown"
    private val release = identifier(release) ?: "local"

    fun sanitize(event: ILoggingEvent): Map<String, Any> = try {
        fields(event)
    } catch (_: Exception) {
        mapOf("level" to "ERROR", "eventCode" to "logging.serialization_failed", "service" to service, "environment" to deployment)
    }

    private fun fields(event: ILoggingEvent): Map<String, Any> {
        val timestamp = Instant.ofEpochMilli(event.timeStamp).toString()
        val request = if (event.message == RequestLogEntry.COMPLETED || event.message == RequestLogEntry.SLOW) {
            event.keyValuePairs?.singleOrNull { it.key == RequestLogEntry.PAYLOAD_KEY }?.value as? RequestLogEntry
        } else {
            null
        }
        val eventCode = when {
            request != null -> event.message
            event.message == SentryPrivacyFilter.SERVICE_READY -> SentryPrivacyFilter.SERVICE_READY
            event.level.isGreaterOrEqual(Level.ERROR) -> "application.error"
            else -> "application.log"
        }
        val fields = linkedMapOf<String, Any>(
            "schemaVersion" to 1,
            "timestamp" to timestamp,
            "service" to service,
            "environment" to deployment,
            "release" to release,
            "level" to event.level.toString(),
            "logger" to (identifier(event.loggerName) ?: "unknown-logger"),
            "eventCode" to eventCode,
        )
        request?.let { fields.putAll(it.fields()) }
        val context = event.mdcPropertyMap
        val traceId = context["traceId"]?.takeIf { TRACE_ID.matches(it) && it.any { character -> character != '0' } }
        if (traceId != null) {
            fields["traceId"] = traceId
            context["spanId"]?.takeIf { SPAN_ID.matches(it) && it.any { character -> character != '0' } }
                ?.let { fields["spanId"] = it }
        }
        event.throwableProxy?.let { fields["exceptions"] = exceptions(it) }
        return fields
    }

    private fun exceptions(source: IThrowableProxy): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        var current: IThrowableProxy? = source
        var remainingFrames = properties.maxStackFrames
        while (current != null && result.size < properties.maxExceptionDepth) {
            val frames = current.stackTraceElementProxyArray.orEmpty().take(remainingFrames).map { proxy ->
                val frame = proxy.stackTraceElement
                mapOf(
                    "class" to (identifier(frame.className) ?: "unknown"),
                    "method" to (identifier(frame.methodName) ?: "unknown"),
                    "file" to (identifier(frame.fileName) ?: "unknown"),
                    "line" to frame.lineNumber,
                )
            }
            result.add(mapOf("type" to (identifier(current.className) ?: "unknown"), "frames" to frames))
            remainingFrames -= frames.size
            current = current.cause
        }
        return result
    }

    private fun identifier(value: String?): String? = value?.takeIf { it.length <= MAX_IDENTIFIER_LENGTH && IDENTIFIER.matches(it) }

    companion object {
        private const val MAX_IDENTIFIER_LENGTH = 128
        private val IDENTIFIER = Regex("[A-Za-z0-9_.$<>:/-]+")
        private val TRACE_ID = Regex("[0-9a-f]{32}")
        private val SPAN_ID = Regex("[0-9a-f]{16}")
    }
}
