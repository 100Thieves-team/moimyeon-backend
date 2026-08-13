package io.plady.moimyeon.core.domain.roomviewer

import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionFinder
import io.plady.moimyeon.core.enums.RoomStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

// "조회하지 않는다"는 mock 으로만 보인다. 관계 조립 자체는 RoomViewerReaderIT 가 실물로 본다.
class RoomViewerReaderTest {
    private val participationFinder: ParticipationFinder = mockk()
    private val memberFinder: MemberFinder = mockk()
    private val roomApplicationSubmissionFinder: RoomApplicationSubmissionFinder = mockk()
    private val clock: Clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val reader = RoomViewerReader(
        participationFinder,
        memberFinder,
        roomApplicationSubmissionFinder,
        clock,
    )

    // 비로그인 탐색이 로그인 사용자와 같은 수의 쿼리를 쏘면 안 된다. 판정 결과가 어차피
    // ANONYMOUS 하나라 조회할 이유가 없다.
    @Test
    fun `비로그인 조회는 관계 질의를 하지 않고 익명으로 채운다`() {
        val roomId = UUID.randomUUID()

        val viewers = reader.readAll(null, mapOf(roomId to room()))

        assertThat(viewers.getValue(roomId).relation).isEqualTo(ViewerRelation.ANONYMOUS)
        assertThat(viewers.getValue(roomId).actions).containsExactly(ViewerAction.LOGIN_REQUIRED)
        verify(exactly = 0) { participationFinder.getRoomParticipations(any(), any()) }
        verify(exactly = 0) { participationFinder.hasAvailableSlot(any()) }
        verify(exactly = 0) { memberFinder.isActive(any()) }
        verify(exactly = 0) { roomApplicationSubmissionFinder.getLatestStatusByRooms(any(), any()) }
        verify(exactly = 0) { roomApplicationSubmissionFinder.hasAvailableQuota(any()) }
    }

    // 빈 IN 절이 쿼리에 들어가지 않게 한다(RoomSearchReader 와 같은 이유).
    @Test
    fun `조회할 룸이 없으면 로그인 사용자여도 아무것도 묻지 않는다`() {
        val viewers = reader.readAll(UUID.randomUUID(), emptyMap())

        assertThat(viewers).isEmpty()
        verify(exactly = 0) { memberFinder.isActive(any()) }
        verify(exactly = 0) { participationFinder.hasAvailableSlot(any()) }
        verify(exactly = 0) { roomApplicationSubmissionFinder.hasAvailableQuota(any()) }
    }

    private fun room(): RoomApplicability = RoomApplicability(
        status = RoomStatus.RECRUITING,
        startAt = NOW.plusDays(1),
        currentParticipants = 3,
        maxCapacity = 8,
    )

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 13, 12, 0)
    }
}
