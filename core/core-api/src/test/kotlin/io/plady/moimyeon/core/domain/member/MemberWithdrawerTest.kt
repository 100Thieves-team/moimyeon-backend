package io.plady.moimyeon.core.domain.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionManager
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

class MemberWithdrawerTest {
    private val memberRepository = mockk<MemberRepository>()
    private val roomApplicationSubmissionManager = mockk<RoomApplicationSubmissionManager>(relaxed = true)
    private val withdrawer = MemberWithdrawer(memberRepository, roomApplicationSubmissionManager)
    private val memberId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)

    @Test
    fun `탈퇴하면 회원을 잠가 소프트 삭제하고 대기 중인 참가 신청을 모두 철회한다`() {
        val member = member()
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns member

        withdrawer.withdraw(memberId, now)

        assertThat(member.isDeleted()).isTrue()
        verify(exactly = 1) { roomApplicationSubmissionManager.withdrawAllPending(memberId, now) }
    }

    @Test
    fun `없는 회원이면 E1006 을 던지고 아무것도 정리하지 않는다`() {
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns null

        assertThatThrownBy { withdrawer.withdraw(memberId, now) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
        verify(exactly = 0) { roomApplicationSubmissionManager.withdrawAllPending(any(), any()) }
    }

    private fun member() = MemberEntity(
        id = memberId,
        email = "user@example.com",
        nickname = "차분한 펭귄 12",
        status = MemberStatus.ACTIVE,
        lastLoginAt = now,
        socialAccounts = mutableListOf(SocialAccountEntity(SocialLoginProvider.GOOGLE, "sub-1", "user@example.com")),
    )
}
