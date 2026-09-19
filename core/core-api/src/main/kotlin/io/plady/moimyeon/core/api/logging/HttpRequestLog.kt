package io.plady.moimyeon.core.api.logging

import io.plady.moimyeon.support.logging.RequestLogEntry
import io.plady.moimyeon.support.logging.RequestLogWriter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class HttpRequestLog(
    private val response: HttpServletResponse,
    private val method: String,
    private val startedAt: Long,
) {
    val requestId: String = UUID.randomUUID().toString()
    private val traceId = MDC.get("traceId")
    private val spanId = MDC.get("spanId")
    private val completed = AtomicBoolean()
    private val routeCaptured = AtomicBoolean()

    @Volatile
    var route: String = "UNMATCHED"

    @Volatile
    private var error: Pair<Int, String>? = null

    fun error(status: Int, code: String) {
        error = status to code
    }

    fun recordRoute(pattern: String) {
        if (!routeCaptured.compareAndSet(false, true)) return
        val matched = RequestLogEntry.routeOrUnmatched(pattern)
        if (matched != "UNMATCHED") route = matched
    }

    fun withContext(block: () -> Unit) {
        val previous = MDC.getCopyOfContextMap()
        try {
            MDC.put("requestId", requestId)
            if (traceId != null) MDC.put("traceId", traceId) else MDC.remove("traceId")
            if (spanId != null) MDC.put("spanId", spanId) else MDC.remove("spanId")
            block()
        } finally {
            if (previous == null) MDC.clear() else MDC.setContextMap(previous)
        }
    }

    fun complete(writer: RequestLogWriter, completedAt: Long) {
        if (!completed.compareAndSet(false, true)) return
        withContext {
            val status = response.status
            writer.write(
                RequestLogEntry(
                    method = RequestLogEntry.methodOrUnknown(method),
                    routeTemplate = RequestLogEntry.routeOrUnmatched(route),
                    status = status,
                    durationMs = TimeUnit.NANOSECONDS.toMillis((completedAt - startedAt).coerceAtLeast(0)),
                    errorCode = error?.takeIf { it.first == status }?.second,
                    requestId = requestId,
                ),
            )
        }
    }

    companion object {
        const val ATTRIBUTE = "io.plady.moimyeon.request-log"

        fun from(request: HttpServletRequest): HttpRequestLog? = request.getAttribute(ATTRIBUTE) as? HttpRequestLog
    }
}
