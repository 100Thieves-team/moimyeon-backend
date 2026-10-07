package io.plady.moimyeon.core.qa

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.progress.RoomProgressManager
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.qa.QaTestDataRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class QaRoomAutoCompleterTest {
    private val qaTestDataRepository = mockk<QaTestDataRepository>()
    private val roomProgressManager = mockk<RoomProgressManager>()
    private val clock = Clock.fixed(Instant.parse("2026-10-07T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val qaRoomAutoCompleter = QaRoomAutoCompleter(qaTestDataRepository, roomProgressManager, clock)

    private val roomId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 10, 7, 12, 0)

    @Test
    fun `자동 완료 조건이 되면 지금 시각으로 완료하고 COMPLETED 를 돌려준다`() {
        every { qaTestDataRepository.findRoom(roomId) } returnsMany listOf(room("[QA] 자동 완료", RoomStatus.CONFIRMED), room("[QA] 자동 완료", RoomStatus.COMPLETED))
        every { roomProgressManager.completeOverdue(roomId, now) } returns true

        assertThat(qaRoomAutoCompleter.complete(roomId)).isEqualTo(QaRoomAutoCompletion(roomId, completed = true, status = RoomStatus.COMPLETED))
    }

    @Test
    fun `그사이 자동 완료 작업이 먼저 끝냈으면 completed=false 와 다시 읽은 COMPLETED 를 돌려준다`() {
        every { qaTestDataRepository.findRoom(roomId) } returnsMany listOf(room("[QA] 경합", RoomStatus.CONFIRMED), room("[QA] 경합", RoomStatus.COMPLETED))
        every { roomProgressManager.completeOverdue(roomId, now) } returns false

        assertThat(qaRoomAutoCompleter.complete(roomId)).isEqualTo(QaRoomAutoCompletion(roomId, completed = false, status = RoomStatus.COMPLETED))
    }

    @Test
    fun `조건이 안 되면 완료하지 않고 지금 상태를 돌려준다`() {
        every { qaTestDataRepository.findRoom(roomId) } returns room("[QA] 아직", RoomStatus.CONFIRMED)
        every { roomProgressManager.completeOverdue(roomId, now) } returns false

        assertThat(qaRoomAutoCompleter.complete(roomId)).isEqualTo(QaRoomAutoCompletion(roomId, completed = false, status = RoomStatus.CONFIRMED))
    }

    @Test
    fun `QA 마커가 없는 룸은 E2201 로 거절하고 완료를 부르지 않는다`() {
        every { qaTestDataRepository.findRoom(roomId) } returns room("실데이터 룸", RoomStatus.CONFIRMED)

        assertThatThrownBy { qaRoomAutoCompleter.complete(roomId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
            }
        verify(exactly = 0) { roomProgressManager.completeOverdue(any(), any()) }
    }

    @Test
    fun `룸이 없으면 E1405 로 거절한다`() {
        every { qaTestDataRepository.findRoom(roomId) } returns null

        assertThatThrownBy { qaRoomAutoCompleter.complete(roomId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_NOT_FOUND)
            }
    }

    private fun room(title: String, status: RoomStatus): RoomEntity = mockk {
        every { this@mockk.title } returns title
        every { this@mockk.status } returns status
    }
}
