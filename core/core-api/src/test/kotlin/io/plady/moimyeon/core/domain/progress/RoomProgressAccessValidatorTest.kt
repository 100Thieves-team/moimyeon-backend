package io.plady.moimyeon.core.domain.progress

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.Optional
import java.util.UUID

class RoomProgressAccessValidatorTest {
    private val roomRepository = mockk<RoomRepository>()
    private val finder = mockk<ParticipationFinder>()
    private val participationValidator = mockk<ParticipationValidator>()
    private val validator = RoomProgressAccessValidator(roomRepository, finder, participationValidator, Clock.systemUTC())
    private val roomId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()

    @Test
    fun `확정 룸 완료는 현재 방장만 통과한다`() {
        val room = mockk<RoomEntity> {
            every { isActive() } returns true
            every { canComplete() } returns true
        }
        every { roomRepository.findById(roomId) } returns Optional.of(room)
        justRun { participationValidator.validateHost(roomId, hostId) }

        validator.validateCompleter(roomId, hostId)

        verify(exactly = 1) { participationValidator.validateHost(roomId, hostId) }
    }

    @Test
    fun `완료된 룸 출석 기록은 현재 방장만 통과한다`() {
        val room = mockk<RoomEntity> {
            every { isActive() } returns true
            every { status } returns RoomStatus.COMPLETED
        }
        every { roomRepository.findById(roomId) } returns Optional.of(room)
        justRun { participationValidator.validateHost(roomId, hostId) }

        validator.validateAttendanceRecorder(roomId, hostId)

        verify(exactly = 1) { participationValidator.validateHost(roomId, hostId) }
    }
}
