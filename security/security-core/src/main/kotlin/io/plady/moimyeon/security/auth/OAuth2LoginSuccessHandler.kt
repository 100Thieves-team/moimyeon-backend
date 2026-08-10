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

        // 가입 커밋(resolve)과 세션 저장(open)은 의도적으로 별도 트랜잭션이다 — 세션 저장이
        // 실패해도 재로그인이 기존 회원 경로로 흘러 복구되므로 원자성을 요구하지 않는다.
        val member = socialMemberResolver.resolve(
            provider = SocialLoginProvider.GOOGLE,
            providerId = subject,
            email = oidcUser.email,
        )

        // Access Token + Refresh Token 둘 다 발급
        val accessToken = jwtTokenProvider.issue(member.id, member.role)
        val session = sessionIssuer.open(member.id)
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
