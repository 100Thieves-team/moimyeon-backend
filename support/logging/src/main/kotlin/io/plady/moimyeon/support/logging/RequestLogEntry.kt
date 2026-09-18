package io.plady.moimyeon.support.logging

// routeTemplate must come from a server-registered mapping, never directly from the request URI.
data class RequestLogEntry(
    val method: String,
    val routeTemplate: String,
    val status: Int,
    val durationMs: Long,
    val errorCode: String? = null,
) {
    init {
        require(method in METHODS) { "Invalid HTTP method for request log" }
        require(routeTemplate.length <= 256 && (routeTemplate == "UNMATCHED" || routeTemplate == "/" || ROUTE.matches(routeTemplate))) { "Invalid route template for request log" }
        require(status in 100..599) { "Invalid HTTP status for request log" }
        require(durationMs >= 0) { "Invalid duration for request log" }
        require(errorCode == null || ERROR_CODE.matches(errorCode)) { "Invalid error code for request log" }
    }

    internal fun fields(): Map<String, Any> = buildMap {
        put("method", method)
        put("route", routeTemplate)
        put("status", status)
        put("durationMs", durationMs)
        errorCode?.let { put("errorCode", it) }
    }

    companion object {
        internal const val PAYLOAD_KEY = "request"
        internal const val COMPLETED = "http.request.completed"
        internal const val SLOW = "http.request.slow"
        private val METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT")
        private val ROUTE = Regex("(?:/(?:[A-Za-z0-9._-]+|\\{[A-Za-z][A-Za-z0-9_]*}))+/?")
        private val ERROR_CODE = Regex("E[0-9]{3,6}")
    }
}
