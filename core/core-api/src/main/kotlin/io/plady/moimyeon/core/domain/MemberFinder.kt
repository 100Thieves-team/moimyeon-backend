package io.plady.moimyeon.core.domain

import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MemberFinder(
    private val memberRepository: MemberRepository,
) {
    fun findBySocialAccount(provider: SocialLoginProvider, providerId: String): Member? {
        val entity = memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderId(provider, providerId)
        return entity?.let(MemberMapper::toDomain)
    }

    fun findById(memberId: UUID): Member? {
        val entity = memberRepository.findById(memberId).orElse(null)
        return entity?.let(MemberMapper::toDomain)
    }
}
