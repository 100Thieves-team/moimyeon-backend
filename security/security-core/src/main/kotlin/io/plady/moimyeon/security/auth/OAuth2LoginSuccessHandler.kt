package io.plady.moimyeon.security.auth

import io.plady.moimyeon.core.enums.SocialLoginProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginSuccessHandler(
    private val socialLoginLander: SocialLoginLander,
    private val oauth2LoginFailureHandler: OAuth2LoginFailureHandler,
) : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val landing = try {
            land(authentication)
        } catch (exception: Exception) {
            oauth2LoginFailureHandler.onLoginProcessingFailure(response, exception)
            return
        }

        landing.cookies.forEach { response.addHeader(HttpHeaders.SET_COOKIE, it.toString()) }
        response.sendRedirect(landing.redirectUri.toASCIIString())
    }

    private fun land(authentication: Authentication): SocialLanding {
        val oidcUser = authentication.principal as OidcUser

        // sub 는 OIDC 규격상 항상 존재한다. 없으면 구조 불변식 위반이며 실패 리다이렉트로 닫는다.
        val subject = requireNotNull(oidcUser.subject) { "OIDC principal 에 sub(subject) 가 없습니다." }
        return socialLoginLander.land(provider = SocialLoginProvider.GOOGLE, providerId = subject, email = oidcUser.email)
    }
}
