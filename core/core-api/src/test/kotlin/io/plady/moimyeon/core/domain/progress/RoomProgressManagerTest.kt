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
import io.plady.moimyeon.core.event.growth.GrowthEvent
import io.plady.moimyeon.core.event.growth.GrowthEventCode
import io.plady.moimyeon.core.event.growth.GrowthEventPublisher
import io.plady.moimyeon.core.event.growth.RoomCompletionTrigger
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
    private val growthEventPublisher = mockk<GrowthEventPublisher>(relaxed = true)
    private val roomProgressManager = RoomProgressManager(
        roomRepository,
        participantFinder,
        participationValidator,
        attendanceRepository,
        logRepository,
        outboxEventPublisher,
        growthEventPublisher,
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
    fun `방장이 완료하면 방장 주체로 확정·출석 인원을 담아 room_completed 사건을 발행한다`() {
        every { roomRepository.findByIdForUpdate(roomId) } returns completableRoom()
        every { participantFinder.getConfirmedParticipantIds(roomId) } returns listOf(hostId, participantId)
        every { logRepository.save(any()) } answers { firstArg() }
        every { attendanceRepository.saveAllAndFlush(any<List<AttendanceEntity>>()) } answers { firstArg() }
        val attendances = listOf(Attendance(hostId, AttendanceStatus.ATTENDED), Attendance(participantId, AttendanceStatus.ABSENT))

        roomProgressManager.complete(RoomProgressCompletionCommand(roomId, hostId, attendances, now))

        verify(exactly = 1) {
            growthEventPublisher.publish(
                GrowthEvent(
                    GrowthEventCode.ROOM_COMPLETED,
                    hostId,
                    mapOf("roomId" to roomId, "completedBy" to RoomCompletionTrigger.HOST, "confirmedCount" to 2, "attendedCount" to 1),
                ),
            )
        }
    }

    @Test
    fun `일정이 지나 자동 완료되면 현재 방장 주체로 SYSTEM 완료 사건을 발행한다`() {
        val room = completableRoom()
        every { room.isAutoCompletable(now) } returns true
        every { roomRepository.findByIdForUpdate(roomId) } returns room
        every { participantFinder.getConfirmedParticipantIds(roomId) } returns listOf(hostId, participantId)
        every { participantFinder.getHostMemberId(roomId) } returns hostId
        every { logRepository.save(any()) } answers { firstArg() }
        every { attendanceRepository.saveAllAndFlush(any<List<AttendanceEntity>>()) } answers { firstArg() }

        roomProgressManager.completeOverdue(roomId, now)

        verify(exactly = 1) {
            growthEventPublisher.publish(
                GrowthEvent(
                    GrowthEventCode.ROOM_COMPLETED,
                    hostId,
                    mapOf("roomId" to roomId, "completedBy" to RoomCompletionTrigger.SYSTEM, "confirmedCount" to 2, "attendedCount" to 2),
                ),
            )
        }
    }

    @Test
    fun `이미 완료된 룸에 같은 출석으로 다시 완료를 보내면 완료 사건을 다시 발행하지 않는다`() {
        val room = completableRoom()
        every { room.status } returns RoomStatus.COMPLETED
        every { roomRepository.findByIdForUpdate(roomId) } returns room
        val attendances = listOf(Attendance(hostId, AttendanceStatus.ATTENDED))
        every { attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId) } returns listOf(
            AttendanceEntity(roomId = roomId, memberId = hostId, status = AttendanceStatus.ATTENDED, recorderMemberId = hostId, recordedAt = now),
        )

        roomProgressManager.complete(RoomProgressCompletionCommand(roomId, hostId, attendances, now))

        verify(exactly = 0) { growthEventPublisher.publish(any()) }
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
