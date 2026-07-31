package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
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
        entity.nickname = nickname.value
        memberRepository.flush()
    }

    @Transactional
    fun withdraw(memberId: UUID, now: LocalDateTime) {
        val entity = requireFound(memberRepository.findByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        entity.delete(now)
    }
}
