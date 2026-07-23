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
    private val socialMemberResolver: SocialMemberResolver,
    private val jwtTokenProvider: JwtTokenProvider,
    private val sessionIssuer: SessionIssuer,
    private val authCookieFactory: AuthCookieFactory,
) : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oidcUser = authentication.principal as OidcUser

        // sub 는 OIDC 규격상 항상 존재한다. 없으면 구조 불변식 위반 → fail-fast.
        val subject = requireNotNull(oidcUser.subject) { "OIDC principal 에 sub(subject) 가 없습니다." }

        val memberId = socialMemberResolver.resolve(
            provider = SocialLoginProvider.GOOGLE,
            providerId = subject,
            email = oidcUser.email,
        )

        // Access Token + Refresh Token 둘 다 발급
        val accessToken = jwtTokenProvider.issue(memberId)
        val session = sessionIssuer.open(memberId)
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.createAccess(accessToken).toString())
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.createRefresh(session).toString())
        response.sendRedirect(FRONTEND_CALLBACK_URL)
    }

    companion object {
        // NOTE: 프론트 콜백 경로.
        // TODO: 환경별 분기는 후속(프로파일/설정 주입).
        private const val FRONTEND_CALLBACK_URL = "https://moimyeon.plady.io/auth/callback"
    }
}
