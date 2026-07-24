package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.response.ApiResponse
import io.plady.moimyeon.security.auth.AuthErrorWriter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ApiResponseAuthErrorWriter(
    private val objectMapper: ObjectMapper,
) : AuthErrorWriter {
    override fun writeUnauthorized(response: HttpServletResponse) = write(response, CoreApiErrorType.AUTHENTICATION_REQUIRED)

    override fun writeForbidden(response: HttpServletResponse) = write(response, CoreApiErrorType.ACCESS_DENIED)

    private fun write(response: HttpServletResponse, errorType: CoreApiErrorType) {
        response.status = errorType.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.writer, ApiResponse.error<Any>(errorType))
    }
}
