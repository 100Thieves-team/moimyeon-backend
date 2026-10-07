package io.plady.moimyeon.core.domain.member

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MemberRestorerTest {
    private val memberRepository = mockk<MemberRepository>()
    private val restorer = MemberRestorer(memberRepository)
    private val memberId = UUID.randomUUID()
    private val withdrawnAt = LocalDateTime.of(2026, 9, 1, 12, 0)
    private val now = LocalDateTime.of(2026, 10, 2, 12, 0)

    @Test
    fun `탈퇴한 회원을 복구하면 탈퇴 시각이 지워지고 로그인 시각이 기록된다`() {
        val member = member(MemberStatus.ACTIVE).apply { delete(withdrawnAt) }
        every { memberRepository.findForUpdateById(memberId) } returns member

        restorer.restore(memberId, now.minusMinutes(1), now)

        assertThat(member.isDeleted()).isFalse()
        assertThat(member.lastLoginAt).isEqualTo(now)
    }

    // 이용 제한과 탈퇴는 따로 움직이는 값이다 — 탈퇴했다가 복구하는 것으로 제한이 풀리면 안 된다(R178).
    @Test
    fun `복구해도 이용 제한 상태는 그대로다`() {
        val member = member(MemberStatus.RESTRICTED).apply { delete(withdrawnAt) }
        every { memberRepository.findForUpdateById(memberId) } returns member

        restorer.restore(memberId, now.minusMinutes(1), now)

        assertThat(member.status).isEqualTo(MemberStatus.RESTRICTED)
    }

    @Test
    fun `이미 복구된 회원이 다시 복구하면 로그인 시각만 기록된다`() {
        val member = member(MemberStatus.ACTIVE)
        every { memberRepository.findForUpdateById(memberId) } returns member

        restorer.restore(memberId, now.minusMinutes(1), now)

        assertThat(member.isDeleted()).isFalse()
        assertThat(member.lastLoginAt).isEqualTo(now)
    }

    @Test
    fun `없는 회원이면 E1006 을 던진다`() {
        every { memberRepository.findForUpdateById(memberId) } returns null

        assertThatThrownBy { restorer.restore(memberId, now.minusMinutes(1), now) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
    }

    // 복구 → 다시 탈퇴 뒤 남은 토큰으로 되살리지 못한다. 토큰 발급 뒤의 탈퇴는 그 토큰과 무관하다.
    @Test
    fun `복구 확인 토큰을 발급한 뒤 다시 탈퇴했다면 그 토큰으로는 복구하지 못하고 E1105 를 던진다`() {
        val confirmedAt = now.minusMinutes(5)
        val member = member(MemberStatus.ACTIVE).apply { delete(now.minusMinutes(1)) }
        every { memberRepository.findForUpdateById(memberId) } returns member

        assertThatThrownBy { restorer.restore(memberId, confirmedAt, now) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESTORATION_EXPIRED)
            }
        assertThat(member.isDeleted()).isTrue()
    }

    private fun member(status: MemberStatus) = MemberEntity(
        id = memberId,
        email = "user@example.com",
        nickname = "차분한 펭귄 12",
        status = status,
        lastLoginAt = withdrawnAt,
        socialAccounts = mutableListOf(SocialAccountEntity(SocialLoginProvider.GOOGLE, "sub-1", "user@example.com")),
    )
}
