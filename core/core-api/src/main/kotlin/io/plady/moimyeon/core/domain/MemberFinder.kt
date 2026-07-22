package io.plady.moimyeon.core.domain

import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component

@Component
class MemberFinder(
    private val memberRepository: MemberRepository,
) {
    fun findBySocialAccount(provider: SocialLoginProvider, providerId: String): Member? = memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderId(provider, providerId)
        ?.let { entity ->
            Member(
                id = entity.id,
                email = Email(entity.email),
                status = entity.status,
                socialAccounts = entity.socialAccounts.map { SocialAccount(it.provider, it.providerId, it.linkedEmail?.let(::Email)) },
                lastLoginAt = entity.lastLoginAt,
                withdrawnAt = entity.withdrawnAt,
            )
        }
}
