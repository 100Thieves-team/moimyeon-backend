package io.plady.moimyeon.core.api.logging

import jakarta.servlet.ServletResponse
import jakarta.servlet.ServletResponseWrapper
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper

internal class RequestLogResponse(
    response: HttpServletResponse,
    private val requestLog: HttpRequestLog,
) : HttpServletResponseWrapper(response) {
    companion object {
        fun recordError(response: HttpServletResponse, status: Int, code: String) {
            var current: ServletResponse = response
            while (current is ServletResponseWrapper) {
                if (current is RequestLogResponse) {
                    current.requestLog.error(status, code)
                    return
                }
                current = current.response
            }
        }
    }
}
