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
import java.net.URI

@Component
class OAuth2LoginSuccessHandler(
    private val socialMemberResolver: SocialMemberResolver,
    private val jwtTokenProvider: JwtTokenProvider,
    private val sessionIssuer: SessionIssuer,
    private val authCookieFactory: AuthCookieFactory,
    private val authProperties: AuthProperties,
    private val oauth2LoginFailureHandler: OAuth2LoginFailureHandler,
    private val restoreTokenProvider: RestoreTokenProvider,
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

    private fun land(authentication: Authentication): Landing {
        val oidcUser = authentication.principal as OidcUser

        // sub 는 OIDC 규격상 항상 존재한다. 없으면 구조 불변식 위반이며 실패 리다이렉트로 닫는다.
        val subject = requireNotNull(oidcUser.subject) { "OIDC principal 에 sub(subject) 가 없습니다." }

        // 가입 커밋(resolve)과 세션 저장(open)은 의도적으로 별도 트랜잭션이다. 세션 저장이 실패해도
        // 재로그인이 기존 회원 경로로 흘러 복구되므로 원자성을 요구하지 않는다.
        val result = socialMemberResolver.resolve(
            provider = SocialLoginProvider.GOOGLE,
            providerId = subject,
            email = oidcUser.email,
        )
        return when (result) {
            is SocialLoginResult.Authenticated -> Landing(
                cookies = issueSessionCookies(result.member),
                redirectUri = authProperties.oauth2.successRedirectUri,
            )
            is SocialLoginResult.Withdrawn -> Landing(
                cookies = listOf(authCookieFactory.createRestore(restoreTokenProvider.issue(result.memberId))),
                redirectUri = authProperties.oauth2.restoreRedirectUri,
            )
        }
    }

    private fun issueSessionCookies(member: AuthenticatedMember): List<ResponseCookie> {
        val accessToken = jwtTokenProvider.issue(member.id, member.role)
        val session = sessionIssuer.open(member.id)
        return listOf(
            authCookieFactory.createAccess(accessToken),
            authCookieFactory.createRefresh(session),
        )
    }

    private data class Landing(
        val cookies: List<ResponseCookie>,
        val redirectUri: URI,
    )
}
