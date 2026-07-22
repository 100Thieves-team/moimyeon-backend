package io.plady.moimyeon.security.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

// 미인증(401) → 공통 포맷. 필터 예외라 @RestControllerAdvice 를 못 타므로 여기서 직접 응답
@Component
class ApiResponseAuthenticationEntryPoint(
    private val authErrorWriter: AuthErrorWriter,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        authErrorWriter.writeUnauthorized(response)
    }
}

// 인가 실패(403) → 공통 포맷.
@Component
class ApiResponseAccessDeniedHandler(
    private val authErrorWriter: AuthErrorWriter,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        authErrorWriter.writeForbidden(response)
    }
}
