package io.plady.moimyeon.support.logging

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KLoggingEventBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration

class RequestLogWriter(
    private val properties: LoggingProperties,
    private val logger: KLogger = KotlinLogging.logger {},
) {
    fun write(entry: RequestLogEntry) {
        val failed = entry.status >= 400
        if (!failed && properties.excludedPaths.any { entry.routeTemplate == it || entry.routeTemplate.startsWith("$it/") }) return
        val slow = !failed && Duration.ofMillis(entry.durationMs) >= properties.slowRequestThreshold
        val event: KLoggingEventBuilder.() -> Unit = {
            message = if (slow) RequestLogEntry.SLOW else RequestLogEntry.COMPLETED
            payload = mapOf(RequestLogEntry.PAYLOAD_KEY to entry)
        }
        // Failure diagnostics belong to the handler; the request summary must not alert twice.
        if (slow) logger.atWarn(event) else logger.atInfo(event)
    }
}
