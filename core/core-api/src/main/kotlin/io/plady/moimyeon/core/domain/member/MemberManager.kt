package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class MemberManager(
    private val memberRepository: MemberRepository,
) {
    @Transactional
    fun recordLogin(provider: SocialLoginProvider, providerId: String): UUID {
        val entity = requireFound(
            memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNull(provider, providerId),
            CoreErrorType.MEMBER_NOT_FOUND,
        )
        entity.loggedIn(LocalDateTime.now())
        return entity.id
    }

    @Transactional
    fun changeNickname(memberId: UUID, nickname: Nickname) {
        val entity = requireFound(memberRepository.findByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        requireBusiness(
            !memberRepository.existsByNicknameAndIdNot(nickname.value, memberId),
            CoreErrorType.NICKNAME_DUPLICATED,
        )

        entity.changeNickname(nickname.value)
        try {
            memberRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            // 기대한 닉네임 충돌(동시 변경 레이스)만 도메인 에러로 번역하고, 그 외 무결성 위반은 전파한다.
            if (e.matchesConstraint(MEMBER_NICKNAME_UNIQUE_CONSTRAINT)) {
                throw CoreException(CoreErrorType.NICKNAME_DUPLICATED)
            }
            throw e
        }
    }

    @Transactional
    fun restrict(memberId: UUID) {
        val entity = requireFound(memberRepository.findByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        requireBusiness(entity.canRestrict(), CoreErrorType.MEMBER_NOT_ACTIVE)
        entity.restrict()
    }

    @Transactional
    fun withdraw(memberId: UUID, now: LocalDateTime) {
        val entity = requireFound(memberRepository.findByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        entity.delete(now)
    }
}
