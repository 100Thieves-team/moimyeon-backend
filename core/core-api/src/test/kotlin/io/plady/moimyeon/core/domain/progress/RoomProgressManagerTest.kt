package io.plady.moimyeon.core.domain.progress

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.event.OutboxEventPublisher
import io.plady.moimyeon.core.event.payload.RoomCompletedEventPayload
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomProgressManagerTest {
    private val roomRepository = mockk<RoomRepository>()
    private val participantFinder = mockk<ParticipationFinder>()
    private val participationValidator = mockk<ParticipationValidator>(relaxed = true)
    private val attendanceRepository = mockk<AttendanceRepository>()
    private val logRepository = mockk<RoomStatusLogRepository>()
    private val outboxEventPublisher = mockk<OutboxEventPublisher>(relaxed = true)
    private val roomProgressManager = RoomProgressManager(
        roomRepository,
        participantFinder,
        participationValidator,
        attendanceRepository,
        logRepository,
        outboxEventPublisher,
    )
    private val roomId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()
    private val participantId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 9, 24, 15, 0)

    @Test
    fun `완료하면서 출석을 저장하고 출석자를 담아 완료 사실을 발행한다`() {
        val room = completableRoom()
        every { roomRepository.findByIdForUpdate(roomId) } returns room
        every { participantFinder.getConfirmedParticipantIds(roomId) } returns listOf(hostId, participantId)
        every { logRepository.save(any()) } answers { firstArg() }
        val saved = slot<List<AttendanceEntity>>()
        every { attendanceRepository.saveAllAndFlush(capture(saved)) } answers { firstArg() }
        val attendances = listOf(Attendance(hostId, AttendanceStatus.ATTENDED), Attendance(participantId, AttendanceStatus.ABSENT))

        val result = roomProgressManager.complete(RoomProgressCompletionCommand(roomId, hostId, attendances, now))

        assertThat(result.attendances).isEqualTo(attendances)
        verifyOrder {
            roomRepository.findByIdForUpdate(roomId)
            participationValidator.validateHost(roomId, hostId)
            room.complete()
        }
        assertThat(saved.captured.map { it.recorderMemberId }).containsOnly(hostId)
        verify(exactly = 1) {
            outboxEventPublisher.publish(
                EventType.ROOM_COMPLETED,
                RoomCompletedEventPayload(roomId, TITLE, hostId, listOf(hostId, participantId), listOf(hostId)),
            )
        }
    }

    @Test
    fun `출석 명단이 확정 참여자와 다르면 완료하지 않는다`() {
        val room = completableRoom()
        every { roomRepository.findByIdForUpdate(roomId) } returns room
        every { participantFinder.getConfirmedParticipantIds(roomId) } returns listOf(hostId, participantId)

        assertThatThrownBy {
            roomProgressManager.complete(RoomProgressCompletionCommand(roomId, hostId, listOf(Attendance(hostId, AttendanceStatus.ATTENDED)), now))
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_PARTICIPANT_MISMATCH)
        }

        verify(exactly = 0) { room.complete() }
        verify(exactly = 0) { outboxEventPublisher.publish(any(), any()) }
    }

    @Test
    fun `룸 잠금 뒤 현재 방장이 아니면 완료하지 않는다`() {
        val room = completableRoom()
        every { roomRepository.findByIdForUpdate(roomId) } returns room
        every { participationValidator.validateHost(roomId, hostId) } throws CoreException(CoreErrorType.ROOM_FORBIDDEN)

        assertThatThrownBy { roomProgressManager.complete(RoomProgressCompletionCommand(roomId, hostId, emptyList(), now)) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_FORBIDDEN)
            }

        verifyOrder {
            roomRepository.findByIdForUpdate(roomId)
            participationValidator.validateHost(roomId, hostId)
        }
        verify(exactly = 0) { room.complete() }
    }

    private fun completableRoom(): RoomEntity = mockk {
        every { isActive() } returns true
        every { id } returns roomId
        every { title } returns TITLE
        every { canComplete() } returns true
        every { complete() } just runs
        every { status } returnsMany listOf(RoomStatus.CONFIRMED, RoomStatus.COMPLETED)
    }

    private companion object {
        const val TITLE = "토스 백엔드 모의면접"
    }
}
