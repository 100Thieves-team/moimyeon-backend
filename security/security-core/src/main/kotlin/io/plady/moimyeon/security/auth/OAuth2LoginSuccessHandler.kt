package io.plady.moimyeon.security.auth

import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.security.config.AuthProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
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
    private val authProperties: AuthProperties,
    private val oauth2LoginFailureHandler: OAuth2LoginFailureHandler,
) : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val cookies = try {
            issueSessionCookies(authentication)
        } catch (exception: Exception) {
            oauth2LoginFailureHandler.onLoginProcessingFailure(response, exception)
            return
        }

        // 두 쿠키가 모두 준비된 뒤에만 헤더에 기록해 부분 로그인 상태를 만들지 않는다.
        cookies.forEach { response.addHeader(HttpHeaders.SET_COOKIE, it.toString()) }
        response.sendRedirect(authProperties.oauth2.successRedirectUri.toASCIIString())
    }

    private fun issueSessionCookies(authentication: Authentication): List<ResponseCookie> {
        val oidcUser = authentication.principal as OidcUser

        // sub 는 OIDC 규격상 항상 존재한다. 없으면 구조 불변식 위반이며 실패 리다이렉트로 닫는다.
        val subject = requireNotNull(oidcUser.subject) { "OIDC principal 에 sub(subject) 가 없습니다." }

        // 가입 커밋(resolve)과 세션 저장(open)은 의도적으로 별도 트랜잭션이다. 세션 저장이 실패해도
        // 재로그인이 기존 회원 경로로 흘러 복구되므로 원자성을 요구하지 않는다.
        val member = socialMemberResolver.resolve(
            provider = SocialLoginProvider.GOOGLE,
            providerId = subject,
            email = oidcUser.email,
        )
        val accessToken = jwtTokenProvider.issue(member.id, member.role)
        val session = sessionIssuer.open(member.id)
        return listOf(
            authCookieFactory.createAccess(accessToken),
            authCookieFactory.createRefresh(session),
        )
    }
}
