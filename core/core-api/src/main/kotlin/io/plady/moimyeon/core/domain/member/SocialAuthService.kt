package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SocialAuthService(
    private val memberFinder: MemberFinder,
    private val memberManager: MemberManager,
    private val memberRegistrationManager: MemberRegistrationManager,
) {
    fun authenticate(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        if (memberFinder.existsBySocialAccount(provider, providerId)) {
            return memberManager.recordLogin(provider, providerId)
        }

        return memberRegistrationManager.register(provider, providerId, email)
    }
}
