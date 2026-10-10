package io.plady.moimyeon.core.domain.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.analytics.AnalyticsIdGenerator
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.room.RoomLeaveManager
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionManager
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class MemberServiceTest {
    private val memberFinder = mockk<MemberFinder>()
    private val nicknameGenerator = mockk<NicknameGenerator>()
    private val memberWithdrawer = mockk<MemberWithdrawer>(relaxed = true)
    private val roomApplicationSubmissionManager = mockk<RoomApplicationSubmissionManager>(relaxed = true)
    private val participationFinder = mockk<ParticipationFinder>()
    private val roomLeaveManager = mockk<RoomLeaveManager>(relaxed = true)
    private val analyticsIdGenerator = mockk<AnalyticsIdGenerator>()
    private val memberService = MemberService(
        memberFinder,
        nicknameGenerator,
        memberWithdrawer,
        mockk(),
        roomApplicationSubmissionManager,
        participationFinder,
        roomLeaveManager,
        analyticsIdGenerator,
        Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `여러 회원 조회는 Finder 결과를 그대로 반환한다`() {
        val memberIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        val members = listOf(mockk<Member>(), mockk<Member>())
        every { memberFinder.getAllByIds(memberIds) } returns members

        val result = memberService.getMembers(memberIds)

        assertThat(result).containsExactlyElementsOf(members)
    }

    @Test
    fun `닉네임 형식이 틀리면 사용 가능 여부 확인은 E1005 를 던진다`() {
        assertThatThrownBy { memberService.isNicknameAvailable("금지문자!@#") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.INVALID_NICKNAME)
            }
    }

    // 룸마다 따로 커밋한다 — 한 트랜잭션에서 여러 룸을 잠그면 뒤 룸의 조회가 앞선 스냅샷을 본다.
    @Test
    fun `탈퇴는 대기 신청 철회 후 룸마다 나가고 마지막에 회원을 정리한다`() {
        val memberId = UUID.randomUUID()
        val roomA = UUID.fromString("00000000-0000-0000-0000-00000000000a")
        val roomB = UUID.fromString("00000000-0000-0000-0000-00000000000b")
        every { participationFinder.getParticipatingRoomIds(memberId) } returns listOf(roomB, roomA)
        every { memberWithdrawer.withdraw(memberId, any(), setOf(roomA, roomB)) } returns true

        memberService.withdraw(memberId)

        verifyOrder {
            roomApplicationSubmissionManager.withdrawAllPending(memberId, any())
            roomLeaveManager.leaveOnWithdrawal(roomA, memberId, any())
            roomLeaveManager.leaveOnWithdrawal(roomB, memberId, any())
            memberWithdrawer.withdraw(memberId, any(), setOf(roomA, roomB))
        }
    }

    @Test
    fun `룸을 나가는 사이 새 참여가 생겨 회원 정리가 거절하면 처음부터 다시 돈다`() {
        val memberId = UUID.randomUUID()
        val roomA = UUID.randomUUID()
        val roomNew = UUID.randomUUID()
        every { participationFinder.getParticipatingRoomIds(memberId) } returnsMany listOf(listOf(roomA), listOf(roomA, roomNew))
        every { memberWithdrawer.withdraw(memberId, any(), setOf(roomA)) } returns false
        every { memberWithdrawer.withdraw(memberId, any(), setOf(roomA, roomNew)) } returns true

        memberService.withdraw(memberId)

        verify(exactly = 1) { roomLeaveManager.leaveOnWithdrawal(roomNew, memberId, any()) }
        verify(exactly = 2) { roomApplicationSubmissionManager.withdrawAllPending(memberId, any()) }
    }

    @Test
    fun `세 번 시도해도 회원 정리가 거절하면 E1014 를 던진다`() {
        val memberId = UUID.randomUUID()
        every { participationFinder.getParticipatingRoomIds(memberId) } returns emptyList()
        every { memberWithdrawer.withdraw(memberId, any(), any()) } returns false

        assertThatThrownBy { memberService.withdraw(memberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_WITHDRAWAL_INTERRUPTED)
            }
        verify(exactly = 3) { memberWithdrawer.withdraw(memberId, any(), any()) }
    }

    @Test
    fun `내 정보용 analyticsId는 회원 ID로 생성기에서 받는다`() {
        val memberId = UUID.randomUUID()
        every { analyticsIdGenerator.generate(memberId) } returns "0123456789abcdef0123456789abcdef"

        assertThat(memberService.getAnalyticsId(memberId)).isEqualTo("0123456789abcdef0123456789abcdef")
    }
}
