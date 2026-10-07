package io.plady.moimyeon.core.api.logging

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class HttpRequestLoggingFilter(
    private val nanoTime: () -> Long = System::nanoTime,
) : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (request !is HttpServletRequest || response !is HttpServletResponse) {
            chain.doFilter(request, response)
            return
        }
        val log = HttpRequestLog.from(request) ?: HttpRequestLog(response, request.method, nanoTime()).also {
            it.route = securityRoute(request)
            request.setAttribute(HttpRequestLog.ATTRIBUTE, it)
        }
        log.withContext { chain.doFilter(request, RequestLogResponse(response, log)) }
    }

    private fun securityRoute(request: HttpServletRequest): String {
        val path = request.requestURI.removePrefix(request.contextPath)
        return when {
            OAUTH_START.matches(path) -> "/oauth2/authorization/{registrationId}"
            OAUTH_CALLBACK.matches(path) -> "/login/oauth2/code/{registrationId}"
            path == "/health" -> "/health"
            path == "/actuator/health" -> "/actuator/health"
            HEALTH_GROUP.matches(path) -> "/actuator/health/{group}"
            else -> "UNMATCHED"
        }
    }

    companion object {
        private val OAUTH_START = Regex("/oauth2/authorization/[^/]+")
        private val OAUTH_CALLBACK = Regex("/login/oauth2/code/[^/]+")
        private val HEALTH_GROUP = Regex("/actuator/health/[^/]+")
    }
}
