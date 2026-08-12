package io.plady.moimyeon.security.auth

import io.plady.moimyeon.security.config.AuthProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
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
        // 공급자 상세 설명은 제외하되 안전한 오류 코드는 남겨 정상 거절과 설정 오류를 구분한다.
        val errorCode = (exception as? OAuth2AuthenticationException)?.error?.errorCode ?: "unknown"
        log.warn("OAuth2 authentication failed: {} ({})", exception.javaClass.simpleName, errorCode)
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
