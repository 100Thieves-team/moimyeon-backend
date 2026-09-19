package io.plady.moimyeon.core.api.logging

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

class RequestLogRouteInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.dispatcherType != DispatcherType.ERROR) {
            val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
            if (pattern != null) HttpRequestLog.from(request)?.recordRoute(pattern)
        }
        return true
    }
}
