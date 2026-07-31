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
    fun append(provider: SocialLoginProvider, providerId: String, email: Email, nickname: Nickname): UUID {
        val member = Member.register(provider, providerId, email, nickname, LocalDateTime.now())
        // 유니크 충돌(동시 가입)을 호출자 트랜잭션 안에서 잡을 수 있게 즉시 flush함.
        return memberRepository.saveAndFlush(MemberMapper.toEntity(member)).id
    }

    @Transactional
    fun recordLogin(memberId: UUID) {
        val entity = requireFound(memberRepository.findByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        entity.loggedIn(LocalDateTime.now())
    }

    @Transactional
    fun changeNickname(memberId: UUID, nickname: Nickname) {
        val entity = requireFound(memberRepository.findByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        entity.changeNickname(nickname.value)
        try {
            memberRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            // 기대한 닉네임 충돌(동시 변경 레이스)만 도메인 에러로 번역하고, 그 외 무결성 위반은 전파한다.
            if (isNicknameConflict(e)) throw CoreException(CoreErrorType.NICKNAME_DUPLICATED)
            throw e
        }
    }

    companion object {
        // uk_member_nickname 을 아는 유일한 곳. 가입 재시도(MemberProvisioner)도 이 판별을 쓴다.
        internal fun isNicknameConflict(e: DataIntegrityViolationException): Boolean {
            return (e.rootCause?.message ?: e.message).orEmpty().contains("uk_member_nickname", ignoreCase = true)
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
