package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.enums.MemberRole
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import java.time.LocalDateTime
import java.util.UUID

data class Member(
    val id: UUID,
    val email: Email,
    val nickname: Nickname,
    val status: MemberStatus,
    val socialAccounts: List<SocialAccount>,
    val lastLoginAt: LocalDateTime,
    val role: MemberRole = MemberRole.USER,
) {
    init {
        require(socialAccounts.isNotEmpty()) {
            "회원은 최소 하나의 소셜 계정을 가져야 합니다."
        }
        require(socialAccounts.distinctBy { it.provider to it.providerId }.size == socialAccounts.size) {
            "동일한 (provider, providerId) 소셜 계정은 중복될 수 없습니다."
        }
    }

    companion object {
        fun register(
            provider: SocialLoginProvider,
            providerId: String,
            email: Email,
            nickname: Nickname,
            now: LocalDateTime,
        ): Member = Member(
            id = UUID.randomUUID(),
            email = email,
            nickname = nickname,
            status = MemberStatus.ACTIVE,
            socialAccounts = listOf(SocialAccount(provider, providerId, linkedEmail = email)),
            lastLoginAt = now,
            role = MemberRole.USER,
        )
    }
}
