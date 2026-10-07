package io.plady.moimyeon.support.logging

// routeTemplate must come from a server-registered mapping, never directly from the request URI.
data class RequestLogEntry(
    val method: String,
    val routeTemplate: String,
    val status: Int,
    val durationMs: Long,
    val errorCode: String? = null,
    val requestId: String? = null,
) {
    init {
        require(method in METHODS) { "Invalid HTTP method for request log" }
        require(isRouteTemplate(routeTemplate)) { "Invalid route template for request log" }
        require(status in 100..599) { "Invalid HTTP status for request log" }
        require(durationMs >= 0) { "Invalid duration for request log" }
        require(errorCode == null || ERROR_CODE.matches(errorCode)) { "Invalid error code for request log" }
        require(requestId == null || isRequestId(requestId)) { "Invalid request ID for request log" }
    }

    internal fun fields(): Map<String, Any> = buildMap {
        put("method", method)
        put("route", routeTemplate)
        put("status", status)
        put("durationMs", durationMs)
        errorCode?.let { put("errorCode", it) }
        requestId?.let { put("requestId", it) }
    }

    companion object {
        internal const val PAYLOAD_KEY = "request"
        internal const val COMPLETED = "http.request.completed"
        internal const val SLOW = "http.request.slow"
        private val METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT", "UNKNOWN")
        private val ROUTE = Regex("(?:/(?:[A-Za-z0-9._-]+|\\{[A-Za-z][A-Za-z0-9_]*}))+/?")
        private val ERROR_CODE = Regex("E[0-9]{3,6}")
        private val REQUEST_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        fun methodOrUnknown(method: String): String = method.takeIf { it in METHODS } ?: "UNKNOWN"

        fun routeOrUnmatched(route: String?): String = route?.takeIf(::isRouteTemplate) ?: "UNMATCHED"

        internal fun isRequestId(value: String): Boolean = REQUEST_ID.matches(value)

        private fun isRouteTemplate(value: String): Boolean = value.length <= 256 && (value == "UNMATCHED" || value == "/" || ROUTE.matches(value))
    }
}
