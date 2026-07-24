package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import java.time.LocalDateTime
import java.util.UUID

data class Member(
    val id: UUID,
    val email: Email,
    val status: MemberStatus,
    val socialAccounts: List<SocialAccount>,
    val lastLoginAt: LocalDateTime,
    val withdrawnAt: LocalDateTime?,
) {
    init {
        require(socialAccounts.isNotEmpty()) {
            "회원은 최소 하나의 소셜 계정을 가져야 합니다."
        }
        require(socialAccounts.distinctBy { it.provider to it.providerId }.size == socialAccounts.size) {
            "동일한 (provider, providerId) 소셜 계정은 중복될 수 없습니다."
        }
        require((status == MemberStatus.WITHDRAWN) == (withdrawnAt != null)) {
            "withdrawnAt은 WITHDRAWN 상태일 때만 존재해야 합니다. status=$status, withdrawnAt=$withdrawnAt"
        }
    }

    fun restrict(): Member {
        requireBusiness(status == MemberStatus.ACTIVE, CoreErrorType.MEMBER_NOT_ACTIVE)
        return copy(status = MemberStatus.RESTRICTED)
    }

    fun reactivate(): Member {
        requireBusiness(status == MemberStatus.RESTRICTED, CoreErrorType.MEMBER_NOT_RESTRICTED)
        return copy(status = MemberStatus.ACTIVE)
    }

    fun recordLogin(now: LocalDateTime): Member {
        requireBusiness(status != MemberStatus.WITHDRAWN, CoreErrorType.MEMBER_ALREADY_WITHDRAWN)
        return copy(lastLoginAt = now)
    }

    fun withdraw(now: LocalDateTime): Member {
        requireBusiness(status != MemberStatus.WITHDRAWN, CoreErrorType.MEMBER_ALREADY_WITHDRAWN)
        return copy(status = MemberStatus.WITHDRAWN, withdrawnAt = now)
    }

    companion object {
        fun register(
            provider: SocialLoginProvider,
            providerId: String,
            email: Email,
            now: LocalDateTime,
        ): Member = Member(
            id = UUID.randomUUID(),
            email = email,
            status = MemberStatus.ACTIVE,
            socialAccounts = listOf(SocialAccount(provider, providerId, linkedEmail = email)),
            lastLoginAt = now,
            withdrawnAt = null,
        )
    }
}
