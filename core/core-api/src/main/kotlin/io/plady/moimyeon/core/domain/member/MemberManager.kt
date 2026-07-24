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
    fun append(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        val member = Member.register(provider, providerId, email, LocalDateTime.now())
        return memberRepository.save(MemberMapper.toEntity(member)).id
    }

    @Transactional
    fun recordLogin(memberId: UUID) {
        val entity = requireFound(memberRepository.findById(memberId).orElse(null), CoreErrorType.MEMBER_NOT_FOUND)
        entity.loggedIn(LocalDateTime.now())
    }
}
