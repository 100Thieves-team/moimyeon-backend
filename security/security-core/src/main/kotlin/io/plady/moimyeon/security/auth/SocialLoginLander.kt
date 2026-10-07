package io.plady.moimyeon.security.auth

import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.security.config.AuthProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.net.URI
import java.util.UUID

/**
 * 소셜 인증이 끝난 계정을 회원으로 확정하고 심을 쿠키와 이동할 화면을 정한다.
 * OAuth2 성공 처리와 dev QA 로그인이 같은 코드를 타도록 따로 둔다.
 */
@Component
class SocialLoginLander(
    private val socialMemberResolver: SocialMemberResolver,
    private val jwtTokenProvider: JwtTokenProvider,
    private val sessionIssuer: SessionIssuer,
    private val authCookieFactory: AuthCookieFactory,
    private val authProperties: AuthProperties,
    private val restoreTokenProvider: RestoreTokenProvider,
) {
    // 가입 커밋(resolve)과 세션 저장(open)은 의도적으로 별도 트랜잭션이다. 세션 저장이 실패해도
    // 재로그인이 기존 회원 경로로 흘러 복구되므로 원자성을 요구하지 않는다.
    fun land(provider: SocialLoginProvider, providerId: String, email: String?): SocialLanding = when (val result = socialMemberResolver.resolve(provider = provider, providerId = providerId, email = email)) {
        is SocialLoginResult.Authenticated -> SocialLanding(
            memberId = result.member.id,
            outcome = SocialLanding.Outcome.LOGGED_IN,
            cookies = issueSessionCookies(result.member),
            redirectUri = authProperties.oauth2.successRedirectUri,
        )
        is SocialLoginResult.Withdrawn -> SocialLanding(
            memberId = result.memberId,
            outcome = SocialLanding.Outcome.RESTORE_REQUIRED,
            cookies = listOf(authCookieFactory.createRestore(restoreTokenProvider.issue(result.memberId))),
            redirectUri = authProperties.oauth2.restoreRedirectUri,
        )
    }

    private fun issueSessionCookies(member: AuthenticatedMember): List<ResponseCookie> {
        val accessToken = jwtTokenProvider.issue(member.id, member.role)
        val session = sessionIssuer.open(member.id)
        return listOf(
            authCookieFactory.createAccess(accessToken),
            authCookieFactory.createRefresh(session),
        )
    }
}

data class SocialLanding(
    val memberId: UUID,
    val outcome: Outcome,
    val cookies: List<ResponseCookie>,
    val redirectUri: URI,
) {
    enum class Outcome {
        LOGGED_IN,
        RESTORE_REQUIRED,
    }
}
