package io.plady.moimyeon.core.domain

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
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
        check(status == MemberStatus.ACTIVE) {
            "ACTIVE 상태에서만 이용 제한할 수 있습니다: $status"
        }
        return copy(status = MemberStatus.RESTRICTED)
    }

    fun reactivate(): Member {
        check(status == MemberStatus.RESTRICTED) {
            "RESTRICTED 상태에서만 제한을 해제할 수 있습니다: $status"
        }
        return copy(status = MemberStatus.ACTIVE)
    }

    /** 로그인 시각 갱신. 탈퇴 회원은 불가(제한 회원은 상태 안내를 위해 허용). */
    fun recordLogin(now: LocalDateTime): Member {
        check(status != MemberStatus.WITHDRAWN) {
            "탈퇴한 회원은 로그인할 수 없습니다."
        }
        return copy(lastLoginAt = now)
    }

    /** 소셜 계정 추가 연결(다중 연동 대비, MVP 미사용). */
    fun linkSocialAccount(account: SocialAccount): Member {
        check(status == MemberStatus.ACTIVE) {
            "ACTIVE 회원만 소셜 계정을 연결할 수 있습니다: $status"
        }
        require(socialAccounts.none { it.provider == account.provider && it.providerId == account.providerId }) {
            "이미 연결된 소셜 계정입니다: ${account.provider}/${account.providerId}"
        }
        return copy(socialAccounts = socialAccounts + account)
    }

    /** 회원 탈퇴: 종료 상태로 전이(재진입 불가). */
    fun withdraw(now: LocalDateTime): Member {
        check(status != MemberStatus.WITHDRAWN) {
            "이미 탈퇴한 회원입니다."
        }
        return copy(status = MemberStatus.WITHDRAWN, withdrawnAt = now)
    }

    companion object {
        /**
         * 최초 로그인 시 회원 생성: ACTIVE 상태 + 소셜 계정 1개 연결.
         *
         * 식별자는 애플리케이션에서 생성한다(외부 노출 안정). 시각은 테스트 가능성을 위해 [now]로 주입받는다.
         * (provider, providerId) 중복 검사는 생성 전 도메인 서비스가 수행한다 — §4.9 중복 계정 방지.
         */
        fun register(
            provider: SocialLoginProvider,
            providerId: String,
            email: Email,
            now: LocalDateTime,
        ): Member =
            Member(
                id = UUID.randomUUID(),
                email = email,
                status = MemberStatus.ACTIVE,
                socialAccounts = listOf(SocialAccount(provider, providerId, linkedEmail = email)),
                lastLoginAt = now,
                withdrawnAt = null,
            )
    }
}
