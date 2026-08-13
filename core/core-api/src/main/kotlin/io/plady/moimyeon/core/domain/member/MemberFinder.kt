package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.enums.MemberStatus
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
        val entity = memberRepository.findWithSocialAccountsByIdAndDeletedAtIsNull(memberId)
        return MemberMapper.toDomain(requireFound(entity, CoreErrorType.MEMBER_NOT_FOUND))
    }

    fun getAllByIds(memberIds: Collection<UUID>): List<Member> {
        if (memberIds.isEmpty()) return emptyList()
        return memberRepository.findAllWithSocialAccountsByIdInAndDeletedAtIsNull(memberIds).map(MemberMapper::toDomain)
    }

    fun getAttributionsIncludingWithdrawn(memberIds: Collection<UUID>): List<MemberAttribution> {
        if (memberIds.isEmpty()) return emptyList()
        return memberRepository.findAllById(memberIds).map {
            MemberAttribution(
                id = it.id,
                nickname = it.nickname,
                withdrawn = it.isDeleted(),
            )
        }
    }

    fun existsById(memberId: UUID): Boolean {
        return memberRepository.existsByIdAndDeletedAtIsNull(memberId)
    }

    // 제재 여부를 예외 없이 묻는다. validateActive 와 달리 막는 게 아니라 건너뛰는 판정이라
    // 잠금 읽기도 하지 않는다 — 위임 순회에서 후보 수만큼 불린다.
    fun isActive(memberId: UUID): Boolean {
        return memberRepository.existsByIdAndStatusAndDeletedAtIsNull(memberId, MemberStatus.ACTIVE)
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
