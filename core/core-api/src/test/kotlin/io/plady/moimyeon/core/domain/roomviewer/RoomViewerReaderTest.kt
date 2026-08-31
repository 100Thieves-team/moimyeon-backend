package io.plady.moimyeon.core.domain.roomviewer

import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionFinder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

// "조회하지 않는다"는 mock 으로만 보인다. 사실 조립 자체는 RoomViewerReaderIT 가 실물로 본다.
// 판정(Policy)은 MOI-500 으로 사라졌다 — Reader 는 사실을 모아 그대로 돌려줄 뿐이다.
class RoomViewerReaderTest {
    private val participationFinder: ParticipationFinder = mockk()
    private val memberFinder: MemberFinder = mockk()
    private val roomApplicationSubmissionFinder: RoomApplicationSubmissionFinder = mockk()

    private val reader = RoomViewerReader(
        participationFinder,
        memberFinder,
        roomApplicationSubmissionFinder,
    )

    // 비로그인 탐색이 로그인 사용자와 같은 수의 쿼리를 쏘면 안 된다. 내려줄 사실 자체가 없으므로
    // 조회할 이유가 없다 — 응답의 viewer 는 null 이다.
    @Test
    fun `비로그인 조회는 관계 질의를 하지 않고 null 로 채운다`() {
        val roomId = UUID.randomUUID()

        val viewers = reader.readAll(null, setOf(roomId))

        assertThat(viewers).containsExactlyEntriesOf(mapOf(roomId to null))
        verify(exactly = 0) { participationFinder.getRoomParticipations(any(), any()) }
        verify(exactly = 0) { participationFinder.getSlots(any()) }
        verify(exactly = 0) { memberFinder.isActive(any()) }
        verify(exactly = 0) { roomApplicationSubmissionFinder.getLatestStatusByRooms(any(), any()) }
        verify(exactly = 0) { roomApplicationSubmissionFinder.getPendingApplicationQuota(any()) }
    }

    // 빈 IN 절이 쿼리에 들어가지 않게 한다(RoomSearchReader 와 같은 이유).
    @Test
    fun `조회할 룸이 없으면 로그인 사용자여도 아무것도 묻지 않는다`() {
        val viewers = reader.readAll(UUID.randomUUID(), emptySet())

        assertThat(viewers).isEmpty()
        verify(exactly = 0) { memberFinder.isActive(any()) }
        verify(exactly = 0) { participationFinder.getSlots(any()) }
        verify(exactly = 0) { roomApplicationSubmissionFinder.getPendingApplicationQuota(any()) }
    }
}
