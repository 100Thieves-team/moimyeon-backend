package io.plady.moimyeon.core.domain.member

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class SocialAuthService(
    private val memberFinder: MemberFinder,
    private val memberManager: MemberManager,
    private val memberRegistrationManager: MemberRegistrationManager,
) {
    fun authenticate(provider: SocialLoginProvider, providerId: String, email: Email): SocialAuthentication {
        log.debug { "social-auth.authenticate provider=$provider" }
        if (memberFinder.existsBySocialAccount(provider, providerId)) {
            return SocialAuthentication.LoggedIn(memberManager.recordLogin(provider, providerId))
        }
        if (memberFinder.existsWithdrawnBySocialAccount(provider, providerId)) {
            return SocialAuthentication.Withdrawn(memberFinder.getWithdrawnIdBySocialAccount(provider, providerId))
        }

        return SocialAuthentication.LoggedIn(memberRegistrationManager.register(provider, providerId, email))
    }
}
