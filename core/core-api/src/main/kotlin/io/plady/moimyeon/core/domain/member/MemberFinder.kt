package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MemberFinder(
    private val memberRepository: MemberRepository,
) {
    fun getById(memberId: UUID): Member {
        val entity = memberRepository.findByIdAndDeletedAtIsNull(memberId)
        return MemberMapper.toDomain(requireFound(entity, CoreErrorType.MEMBER_NOT_FOUND))
    }

    fun getAllByIds(memberIds: Collection<UUID>): List<Member> {
        if (memberIds.isEmpty()) return emptyList()
        return memberRepository.findByIdInAndDeletedAtIsNull(memberIds).map(MemberMapper::toDomain)
    }

    fun existsById(memberId: UUID): Boolean {
        return memberRepository.existsByIdAndDeletedAtIsNull(memberId)
    }

    fun existsBySocialAccount(provider: SocialLoginProvider, providerId: String): Boolean {
        return memberRepository.existsBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNull(
            provider,
            providerId,
        )
    }

    fun isNicknameAvailable(nickname: Nickname): Boolean {
        return !memberRepository.existsByNickname(nickname.value)
    }
}
