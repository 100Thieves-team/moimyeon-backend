package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SocialAuthService(
    private val memberFinder: MemberFinder,
    private val memberManager: MemberManager,
    private val memberProvisioner: MemberProvisioner,
) {
    fun authenticate(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        if (memberFinder.existsBySocialAccount(provider, providerId)) {
            return memberManager.recordLogin(provider, providerId)
        }

        requireBusiness(!memberFinder.existsWithdrawnBySocialAccount(provider, providerId), CoreErrorType.MEMBER_ALREADY_WITHDRAWN)
        return memberProvisioner.provision(provider, providerId, email)
    }
}
