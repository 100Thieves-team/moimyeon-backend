package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.security.auth.SocialLanding
import io.plady.moimyeon.security.auth.SocialLoginLander
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Google 인증만 건너뛰고 OAuth2 성공 처리와 같은 [SocialLoginLander] 를 탄다.
 * 그래서 가입·로그인 기록·탈퇴 계정의 복구 확인 쿠키가 실제 로그인과 같게 만들어진다.
 */
@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaSocialLogin(
    private val qaMemberFinder: QaMemberFinder,
    private val socialLoginLander: SocialLoginLander,
    private val properties: QaMemberProperties,
) {
    fun login(memberId: UUID): SocialLanding {
        log.debug { "qa-social-login.login memberId=$memberId" }
        val account = qaMemberFinder.getSocialAccount(memberId)
        return socialLoginLander.land(provider = account.provider, providerId = account.providerId, email = account.email)
    }

    // 처음 보는 소셜 계정으로 로그인한다. 실제 가입과 같이 회원이 새로 생긴다.
    fun signUp(): SocialLanding {
        val key = UUID.randomUUID().toString()
        log.debug { "qa-social-login.signUp" }
        return socialLoginLander.land(
            provider = SocialLoginProvider.GOOGLE,
            providerId = "${QaMemberCreator.PROVIDER_ID_PREFIX}$key",
            email = properties.emailOf(key),
        )
    }
}
