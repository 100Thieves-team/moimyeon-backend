package io.plady.moimyeon.core.domain

import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.ErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
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
        val memberEntity = MemberEntity(
            id = member.id,
            email = member.email.value,
            status = member.status,
            lastLoginAt = member.lastLoginAt,
            withdrawnAt = member.withdrawnAt,
            socialAccounts = member.socialAccounts
                .map { SocialAccountEntity(it.provider, it.providerId, it.linkedEmail?.value) }
                .toMutableList(),
        )

        return memberRepository.save(memberEntity).id
    }

    @Transactional
    fun recordLogin(memberId: UUID) {
        val entity = requireFound(memberRepository.findById(memberId).orElse(null), ErrorType.MEMBER_NOT_FOUND)
        entity.loggedIn(LocalDateTime.now())
    }
}
