package io.plady.moimyeon.core.api.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.support.logging.RequestLogWriter
import jakarta.servlet.ServletRequestEvent
import jakarta.servlet.ServletRequestListener
import jakarta.servlet.http.HttpServletRequest

class HttpRequestLogCompletionListener(
    private val writer: RequestLogWriter,
    private val nanoTime: () -> Long = System::nanoTime,
) : ServletRequestListener {
    override fun requestDestroyed(event: ServletRequestEvent) {
        val request = event.servletRequest as? HttpServletRequest ?: return
        val requestLog = HttpRequestLog.from(request) ?: return
        try {
            requestLog.complete(writer, nanoTime())
        } catch (_: Exception) {
            // Logging failures must not change a response that the application has already produced.
            try {
                requestLog.withContext { log.warn { "http.request.logging_failed" } }
            } catch (_: Exception) {
                // There is no further logging fallback when the logging backend itself fails.
            }
        }
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
