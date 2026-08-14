package io.plady.moimyeon.core.domain.closing

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ClosingAccessValidatorTest {
    private val roomFinder = mockk<RoomFinder>()
    private val roomProgressReader = mockk<RoomProgressReader>()
    private val validator = ClosingAccessValidator(roomFinder, roomProgressReader)
    private val roomId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()

    @Test
    fun `진행 중 룸의 출석 참여자는 클로징에 접근한다`() {
        every { roomFinder.getRoom(roomId) } returns room(RoomStatus.IN_PROGRESS)
        every { roomProgressReader.isAttended(roomId, memberId) } returns true

        validator.validateParticipant(roomId, memberId)

        verify(exactly = 1) { roomProgressReader.isAttended(roomId, memberId) }
    }

    @Test
    fun `진행 중인 룸이 아니면 E1802 를 던지고 출석을 조회하지 않는다`() {
        every { roomFinder.getRoom(roomId) } returns room(RoomStatus.COMPLETED)

        assertThatThrownBy { validator.validateParticipant(roomId, memberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.CLOSING_NOT_AVAILABLE)
            }
        verify(exactly = 0) { roomProgressReader.isAttended(any(), any()) }
    }

    @Test
    fun `불참자는 E1801 을 던진다`() {
        every { roomFinder.getRoom(roomId) } returns room(RoomStatus.IN_PROGRESS)
        every { roomProgressReader.isAttended(roomId, memberId) } returns false

        assertThatThrownBy { validator.validateParticipant(roomId, memberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.CLOSING_SUBMISSION_FORBIDDEN)
            }
    }

    private fun room(roomStatus: RoomStatus): Room = mockk {
        every { status } returns roomStatus
    }
}
