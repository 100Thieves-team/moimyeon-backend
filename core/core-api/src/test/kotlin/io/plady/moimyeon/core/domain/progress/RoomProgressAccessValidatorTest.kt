package io.plady.moimyeon.core.domain.progress

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class RoomProgressAccessValidatorTest {
    private val roomRepository = mockk<RoomRepository>()
    private val participationFinder = mockk<ParticipationFinder>()
    private val validator = RoomProgressAccessValidator(roomRepository, participationFinder)

    private val roomId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val startAt = LocalDateTime.of(2026, 8, 13, 20, 0)

    @Test
    fun `확정 룸의 확정 참여자는 진행을 시작할 수 있다`() {
        val room = givenRoom(RoomStatus.CONFIRMED)
        every { participationFinder.wasConfirmedParticipant(roomId, memberId) } returns true

        assertThatCode { validator.validateStarter(roomId, memberId, startAt) }
            .doesNotThrowAnyException()
        verify(exactly = 1) { room.canStartProgress(startAt) }
    }

    @Test
    fun `예정 시각 전에는 확정 참여자도 진행을 시작할 수 없다`() {
        givenRoom(RoomStatus.CONFIRMED, canStart = false)

        assertValidationFails(CoreErrorType.ROOM_PROGRESS_NOT_STARTABLE) {
            validator.validateStarter(roomId, memberId, startAt.minusNanos(1))
        }

        verify(exactly = 0) { participationFinder.wasConfirmedParticipant(any(), any()) }
    }

    @Test
    fun `진행 중인 룸에는 다시 출석을 제출할 수 없다`() {
        givenRoom(RoomStatus.IN_PROGRESS)

        assertValidationFails(CoreErrorType.ROOM_PROGRESS_NOT_STARTABLE) {
            validator.validateStarter(roomId, memberId, startAt)
        }

        verify(exactly = 0) { participationFinder.wasConfirmedParticipant(any(), any()) }
    }

    @Test
    fun `확정 참여자가 아니면 확정 룸의 진행을 시작할 수 없다`() {
        givenRoom(RoomStatus.CONFIRMED)
        every { participationFinder.wasConfirmedParticipant(roomId, memberId) } returns false

        assertValidationFails(CoreErrorType.ROOM_PROGRESS_START_FORBIDDEN) {
            validator.validateStarter(roomId, memberId, startAt)
        }
    }

    @Test
    fun `진행 중인 룸의 확정 참여자는 자신의 출석을 조회할 수 있다`() {
        givenRoom(RoomStatus.IN_PROGRESS)
        every { participationFinder.wasConfirmedParticipant(roomId, memberId) } returns true

        assertThatCode { validator.validateAttendanceViewer(roomId, memberId) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `종료된 룸의 확정 참여자는 자신의 출석을 조회할 수 있다`() {
        givenRoom(RoomStatus.COMPLETED)
        every { participationFinder.wasConfirmedParticipant(roomId, memberId) } returns true

        assertThatCode { validator.validateAttendanceViewer(roomId, memberId) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `시작 전 룸에서는 출석을 조회할 수 없다`() {
        givenRoom(RoomStatus.CONFIRMED)

        assertValidationFails(CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE) {
            validator.validateAttendanceViewer(roomId, memberId)
        }
    }

    @Test
    fun `진행 중인 룸의 확정 참여자는 레일을 조회할 수 있다`() {
        givenRoom(RoomStatus.IN_PROGRESS)
        every { participationFinder.wasConfirmedParticipant(roomId, memberId) } returns true

        assertThatCode { validator.validateRailViewer(roomId, memberId) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `종료된 룸에서는 진행 레일을 조회할 수 없다`() {
        givenRoom(RoomStatus.COMPLETED)

        assertValidationFails(CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE) {
            validator.validateRailViewer(roomId, memberId)
        }
    }

    @Test
    fun `확정 참여자가 아니면 진행 중인 룸 정보에 접근할 수 없다`() {
        givenRoom(RoomStatus.IN_PROGRESS)
        every { participationFinder.wasConfirmedParticipant(roomId, memberId) } returns false

        assertValidationFails(CoreErrorType.ROOM_PROGRESS_FORBIDDEN) {
            validator.validateRailViewer(roomId, memberId)
        }
    }

    @Test
    fun `존재하지 않는 룸의 진행 정보에는 접근할 수 없다`() {
        every { roomRepository.findById(roomId) } returns Optional.empty()

        assertValidationFails(CoreErrorType.ROOM_NOT_FOUND) {
            validator.validateRailViewer(roomId, memberId)
        }
    }

    @Test
    fun `삭제된 룸의 진행 정보에는 접근할 수 없다`() {
        givenRoom(RoomStatus.IN_PROGRESS, isActive = false)

        assertValidationFails(CoreErrorType.ROOM_NOT_FOUND) {
            validator.validateRailViewer(roomId, memberId)
        }
    }

    private fun givenRoom(
        status: RoomStatus,
        isActive: Boolean = true,
        canStart: Boolean = status == RoomStatus.CONFIRMED,
    ): RoomEntity {
        val room = mockk<RoomEntity> {
            every { this@mockk.status } returns status
            every { this@mockk.isActive() } returns isActive
            every { this@mockk.canStartProgress(any()) } returns canStart
        }
        every { roomRepository.findById(roomId) } returns Optional.of(room)
        return room
    }

    private fun assertValidationFails(errorType: CoreErrorType, action: () -> Unit) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
