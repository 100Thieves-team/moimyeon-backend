package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class MemberFinder(
    private val memberRepository: MemberRepository,
) {
    @Transactional(readOnly = true)
    fun getById(memberId: UUID): Member {
        val entity = memberRepository.findByIdAndStatusNot(memberId, MemberStatus.WITHDRAWN)
        return MemberMapper.toDomain(requireFound(entity, CoreErrorType.MEMBER_NOT_FOUND))
    }

    @Transactional(readOnly = true)
    fun getBySocialAccount(provider: SocialLoginProvider, providerId: String): Member {
        val entity = memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderIdAndStatusNot(
            provider,
            providerId,
            MemberStatus.WITHDRAWN,
        )
        return MemberMapper.toDomain(requireFound(entity, CoreErrorType.MEMBER_NOT_FOUND))
    }

    @Transactional(readOnly = true)
    fun existsBySocialAccount(provider: SocialLoginProvider, providerId: String): Boolean {
        return memberRepository.existsBySocialAccountsProviderAndSocialAccountsProviderIdAndStatusNot(
            provider,
            providerId,
            MemberStatus.WITHDRAWN,
        )
    }

    @Transactional(readOnly = true)
    fun existsWithdrawnBySocialAccount(provider: SocialLoginProvider, providerId: String): Boolean {
        return memberRepository.existsBySocialAccountsProviderAndSocialAccountsProviderIdAndStatus(
            provider,
            providerId,
            MemberStatus.WITHDRAWN,
        )
    }
}
