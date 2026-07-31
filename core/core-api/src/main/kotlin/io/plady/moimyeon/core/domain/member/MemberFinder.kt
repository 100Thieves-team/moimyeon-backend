package io.plady.moimyeon.core.domain.member

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
        val entity = memberRepository.findByIdAndDeletedAtIsNull(memberId)
        return MemberMapper.toDomain(requireFound(entity, CoreErrorType.MEMBER_NOT_FOUND))
    }

    @Transactional(readOnly = true)
    fun getBySocialAccount(provider: SocialLoginProvider, providerId: String): Member {
        val entity = memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNull(
            provider,
            providerId,
        )
        return MemberMapper.toDomain(requireFound(entity, CoreErrorType.MEMBER_NOT_FOUND))
    }

    @Transactional(readOnly = true)
    fun existsBySocialAccount(provider: SocialLoginProvider, providerId: String): Boolean {
        return memberRepository.existsBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNull(
            provider,
            providerId,
        )
    }

    @Transactional(readOnly = true)
    fun isNicknameAvailable(nickname: Nickname): Boolean {
        return !memberRepository.existsByNickname(nickname.value)
    }

    @Transactional(readOnly = true)
    fun isNicknameAvailableFor(memberId: UUID, nickname: Nickname): Boolean {
        return !memberRepository.existsByNicknameAndIdNot(nickname.value, memberId)
    }

    @Transactional(readOnly = true)
    fun existsWithdrawnBySocialAccount(provider: SocialLoginProvider, providerId: String): Boolean {
        return memberRepository.existsBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNotNull(
            provider,
            providerId,
        )
    }
}
