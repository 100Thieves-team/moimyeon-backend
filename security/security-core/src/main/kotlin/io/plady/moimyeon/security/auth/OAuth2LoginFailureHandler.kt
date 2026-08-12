package io.plady.moimyeon.security.auth

import io.plady.moimyeon.security.config.AuthProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginFailureHandler(
    private val authProperties: AuthProperties,
) : AuthenticationFailureHandler {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        // 외부 OAuth 오류의 상세 설명에는 공급자 응답이 섞일 수 있어 타입만 기록하고 클라이언트에는 노출하지 않는다.
        log.warn("OAuth2 authentication failed: {}", exception.javaClass.simpleName)
        redirectToFailure(response)
    }

    fun onLoginProcessingFailure(response: HttpServletResponse, exception: Exception) {
        // 성공 콜백 뒤 회원·세션 처리 실패는 이 핸들러가 응답으로 닫으므로 여기서 원인과 스택을 남긴다.
        log.error("OAuth2 login processing failed", exception)
        redirectToFailure(response)
    }

    private fun redirectToFailure(response: HttpServletResponse) {
        response.sendRedirect(authProperties.oauth2.failureRedirectUri.toASCIIString())
    }
}
