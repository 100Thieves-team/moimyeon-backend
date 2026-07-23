package io.plady.moimyeon.core.domain

import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component

@Component
class MemberFinder(
    private val memberRepository: MemberRepository,
) {
    fun findBySocialAccount(provider: SocialLoginProvider, providerId: String): Member? {
        val entity = memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderId(provider, providerId)
        return entity?.let(MemberMapper::toDomain)
    }
}
