package io.plady.moimyeon.core.domain.progress

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.domain.room.RoomLifecycleNotificationEvent
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.RoomStatus
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
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.util.UUID

class RoomProgressManagerTest {
    private val roomRepository = mockk<RoomRepository>()
    private val participantFinder = mockk<ParticipationFinder>()
    private val participationValidator = mockk<ParticipationValidator>(relaxed = true)
    private val attendanceRepository = mockk<AttendanceRepository>()
    private val logRepository = mockk<RoomStatusLogRepository>()
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val manager = RoomProgressManager(
        roomRepository,
        participantFinder,
        participationValidator,
        attendanceRepository,
        logRepository,
        publisher,
    )
    private val roomId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()
    private val participantId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 9, 24, 15, 0)

    @Test
    fun `완료 시 출석을 저장하지 않고 전원에게 완료 이벤트를 발행한다`() {
        val room = mockk<RoomEntity> {
            every { isActive() } returns true
            every { canComplete() } returns true
            every { complete() } just runs
            every { status } returns RoomStatus.COMPLETED
        }
        every { roomRepository.findByIdForUpdate(roomId) } returns room
        every { participantFinder.getConfirmedParticipantIds(roomId) } returns listOf(hostId, participantId)
        every { logRepository.save(any()) } answers { firstArg() }

        manager.complete(RoomProgressCompletionCommand(roomId, hostId, now))

        verifyOrder {
            roomRepository.findByIdForUpdate(roomId)
            participationValidator.validateHost(roomId, hostId)
        }
        verify(exactly = 0) { attendanceRepository.saveAllAndFlush<AttendanceEntity>(any()) }
        val events = mutableListOf<RoomLifecycleNotificationEvent>()
        verify(exactly = 2) { publisher.publishEvent(capture(events)) }
        assertThat(events).allSatisfy { assertThat(it.eventType).isEqualTo(EventType.ROOM_COMPLETED) }
    }

    @Test
    fun `룸 잠금 뒤 현재 방장이 아니면 완료하지 않는다`() {
        val room = mockk<RoomEntity> {
            every { isActive() } returns true
            every { canComplete() } returns true
            every { complete() } just runs
        }
        every { roomRepository.findByIdForUpdate(roomId) } returns room
        every { participationValidator.validateHost(roomId, hostId) } throws CoreException(CoreErrorType.ROOM_FORBIDDEN)

        assertThatThrownBy { manager.complete(RoomProgressCompletionCommand(roomId, hostId, now)) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_FORBIDDEN)
            }

        verifyOrder {
            roomRepository.findByIdForUpdate(roomId)
            participationValidator.validateHost(roomId, hostId)
        }
        verify(exactly = 0) { room.complete() }
    }

    @Test
    fun `출석 저장 뒤 참석자가 둘 이상이면 리뷰 요청을 발행한다`() {
        val room = mockk<RoomEntity> {
            every { isActive() } returns true
            every { status } returns RoomStatus.COMPLETED
        }
        every { roomRepository.findByIdForUpdate(roomId) } returns room
        every { attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId) } returns emptyList()
        every { participantFinder.getConfirmedParticipantIds(roomId) } returns listOf(hostId, participantId)
        every { attendanceRepository.saveAllAndFlush<AttendanceEntity>(any()) } answers { firstArg() }

        manager.recordAttendances(
            RoomAttendanceRecordCommand(
                roomId,
                hostId,
                listOf(
                    Attendance(hostId, AttendanceStatus.ATTENDED),
                    Attendance(participantId, AttendanceStatus.ATTENDED),
                ),
                now,
            ),
        )

        verifyOrder {
            roomRepository.findByIdForUpdate(roomId)
            participationValidator.validateHost(roomId, hostId)
        }
        val events = mutableListOf<RoomLifecycleNotificationEvent>()
        verify(exactly = 2) { publisher.publishEvent(capture(events)) }
        assertThat(events).allSatisfy { assertThat(it.eventType).isEqualTo(EventType.ROOM_REVIEW_REQUESTED) }
    }

    @Test
    fun `참석자가 한 명이면 리뷰 상대가 없어 리뷰 요청을 발행하지 않는다`() {
        val room = mockk<RoomEntity> {
            every { isActive() } returns true
            every { status } returns RoomStatus.COMPLETED
        }
        every { roomRepository.findByIdForUpdate(roomId) } returns room
        every { attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId) } returns emptyList()
        every { participantFinder.getConfirmedParticipantIds(roomId) } returns listOf(hostId)
        every { attendanceRepository.saveAllAndFlush<AttendanceEntity>(any()) } answers { firstArg() }

        manager.recordAttendances(
            RoomAttendanceRecordCommand(
                roomId,
                hostId,
                listOf(Attendance(hostId, AttendanceStatus.ATTENDED)),
                now,
            ),
        )

        verify(exactly = 0) { publisher.publishEvent(any()) }
    }
}
