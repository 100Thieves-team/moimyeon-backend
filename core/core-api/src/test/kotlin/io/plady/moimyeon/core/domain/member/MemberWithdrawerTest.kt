package io.plady.moimyeon.core.domain.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.notification.WebPushSubscriptionManager
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionManager
import io.plady.moimyeon.core.domain.session.SessionManager
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
    private val participationFinder = mockk<ParticipationFinder> {
        every { getParticipatingRoomIds(any()) } returns emptyList()
    }
    private val roomApplicationSubmissionManager = mockk<RoomApplicationSubmissionManager>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val webPushSubscriptionManager = mockk<WebPushSubscriptionManager>(relaxed = true)
    private val withdrawer = MemberWithdrawer(
        memberRepository,
        participationFinder,
        roomApplicationSubmissionManager,
        sessionManager,
        webPushSubscriptionManager,
    )
    private val memberId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)

    @Test
    fun `탈퇴하면 회원을 잠가 소프트 삭제하고 대기 중인 참가 신청을 모두 철회한다`() {
        val member = member()
        every { memberRepository.findForUpdateById(memberId) } returns member

        withdrawer.withdraw(memberId, now, emptySet())

        assertThat(member.isDeleted()).isTrue()
        verify(exactly = 1) { roomApplicationSubmissionManager.withdrawAllPending(memberId, now) }
    }

    @Test
    fun `탈퇴하면 모든 세션을 끝내고 웹 푸시 등록을 지운다`() {
        every { memberRepository.findForUpdateById(memberId) } returns member()

        withdrawer.withdraw(memberId, now, emptySet())

        verify(exactly = 1) { sessionManager.closeAll(memberId, now) }
        verify(exactly = 1) { webPushSubscriptionManager.unregisterAll(memberId) }
    }

    @Test
    fun `없는 회원이면 E1006 을 던지고 아무것도 정리하지 않는다`() {
        every { memberRepository.findForUpdateById(memberId) } returns null

        assertThatThrownBy { withdrawer.withdraw(memberId, now, emptySet()) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
        verify(exactly = 0) { roomApplicationSubmissionManager.withdrawAllPending(any(), any()) }
        verify(exactly = 0) { sessionManager.closeAll(any(), any()) }
    }

    @Test
    fun `이미 탈퇴한 회원이 다시 탈퇴하면 아무것도 하지 않고 끝난다`() {
        val member = member().apply { delete(now.minusMinutes(5)) }
        every { memberRepository.findForUpdateById(memberId) } returns member

        withdrawer.withdraw(memberId, now, emptySet())

        verify(exactly = 0) { roomApplicationSubmissionManager.withdrawAllPending(any(), any()) }
        verify(exactly = 0) { sessionManager.closeAll(any(), any()) }
    }

    // 회원을 잠근 뒤에는 새 참여가 생길 수 없으니, 나간 룸 밖의 참여가 보이면 그 사이 생긴 것이다.
    // 대기 신청을 먼저 닫아야 방장이 수락 중이던 신청의 결과까지 확인에 잡힌다.
    @Test
    fun `룸을 나가는 사이 새로 참여한 룸이 있으면 회원을 지우지 않고 false 를 돌려준다`() {
        val member = member()
        val leftRoom = UUID.randomUUID()
        every { memberRepository.findForUpdateById(memberId) } returns member
        every { participationFinder.getParticipatingRoomIds(memberId) } returns listOf(leftRoom, UUID.randomUUID())

        val done = withdrawer.withdraw(memberId, now, setOf(leftRoom))

        verifyOrder {
            roomApplicationSubmissionManager.withdrawAllPending(memberId, now)
            participationFinder.getParticipatingRoomIds(memberId)
        }
        assertThat(done).isFalse()
        assertThat(member.isDeleted()).isFalse()
        verify(exactly = 0) { sessionManager.closeAll(any(), any()) }
        verify(exactly = 0) { webPushSubscriptionManager.unregisterAll(any()) }
    }

    @Test
    fun `나간 룸에 남은 참여는 새 참여로 보지 않는다`() {
        val member = member()
        val skippedRoom = UUID.randomUUID()
        every { memberRepository.findForUpdateById(memberId) } returns member
        every { participationFinder.getParticipatingRoomIds(memberId) } returns listOf(skippedRoom)

        val done = withdrawer.withdraw(memberId, now, setOf(skippedRoom))

        assertThat(done).isTrue()
        assertThat(member.isDeleted()).isTrue()
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
