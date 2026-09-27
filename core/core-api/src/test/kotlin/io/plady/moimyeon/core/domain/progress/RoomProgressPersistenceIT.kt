package io.plady.moimyeon.core.domain.progress

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.OutboxRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomProgressPersistenceIT(
    private val manager: RoomProgressManager,
    private val reader: RoomProgressReader,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val attendanceRepository: AttendanceRepository,
    private val logRepository: RoomStatusLogRepository,
    private val outboxRepository: OutboxRepository,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()
    private val participantId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 8, 11, 1, 0)

    @AfterEach
    fun cleanUp() {
        outboxRepository.deleteAll(outboxRepository.findAll().filter { roomId.toString() in it.payload })
        logRepository.deleteAll(logRepository.findAll().filter { it.roomId == roomId })
        attendanceRepository.deleteAll(attendanceRepository.findAll().filter { it.roomId == roomId })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
    }

    @Test
    fun `완료하면서 출석을 함께 저장한다`() {
        seedConfirmedRoom()

        manager.complete(RoomProgressCompletionCommand(roomId, hostId, finalAttendances(), now))

        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.COMPLETED)
        assertThat(reader.getAttendance(roomId, hostId)).isEqualTo(Attendance(hostId, AttendanceStatus.ATTENDED))
        assertThat(reader.getAttendance(roomId, participantId)).isEqualTo(Attendance(participantId, AttendanceStatus.ABSENT))
    }

    @Test
    fun `출석 명단이 확정 참여자 전원과 다르면 완료도 출석도 남기지 않는다`() {
        seedConfirmedRoom()

        assertThatThrownBy {
            manager.complete(RoomProgressCompletionCommand(roomId, hostId, listOf(Attendance(hostId, AttendanceStatus.ATTENDED)), now))
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_PARTICIPANT_MISMATCH)
        }

        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.CONFIRMED)
        assertThat(attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)).isEmpty()
    }

    @Test
    fun `응답을 못 받고 같은 출석으로 다시 완료하면 이전 결과를 돌려주고 다시 기록하지 않는다`() {
        seedConfirmedRoom()
        manager.complete(RoomProgressCompletionCommand(roomId, hostId, finalAttendances(), now))

        val retried = manager.complete(RoomProgressCompletionCommand(roomId, hostId, finalAttendances().reversed(), now.plusMinutes(1)))

        assertThat(retried.status).isEqualTo(RoomStatus.COMPLETED)
        assertThat(logRepository.countByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.COMPLETED)).isEqualTo(1)
        assertThat(attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)).hasSize(2)
        assertThat(completedOutboxCount()).isEqualTo(1)
    }

    @Test
    fun `이미 완료된 룸에 다른 출석으로 다시 완료하면 거절하고 기록을 바꾸지 않는다`() {
        seedConfirmedRoom()
        manager.complete(RoomProgressCompletionCommand(roomId, hostId, finalAttendances(), now))
        val changed = listOf(Attendance(hostId, AttendanceStatus.ATTENDED), Attendance(participantId, AttendanceStatus.ATTENDED))

        assertThatThrownBy { manager.complete(RoomProgressCompletionCommand(roomId, hostId, changed, now.plusMinutes(1))) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_ATTENDANCE_ALREADY_RECORDED)
            }
        assertThat(reader.getAttendance(roomId, participantId)).isEqualTo(Attendance(participantId, AttendanceStatus.ABSENT))
    }

    @Test
    fun `이미 완료된 룸에 방장이 아닌 사람이 같은 출석으로 요청해도 거절한다`() {
        seedConfirmedRoom()
        manager.complete(RoomProgressCompletionCommand(roomId, hostId, finalAttendances(), now))

        assertThatThrownBy { manager.complete(RoomProgressCompletionCommand(roomId, participantId, finalAttendances(), now.plusMinutes(1))) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_FORBIDDEN)
            }
    }

    @Test
    fun `출석 저장이 실패하면 완료 상태와 이력과 완료 사실을 남기지 않는다`() {
        seedConfirmedRoom()
        // 같은 회원의 활성 출석이 이미 있으면 유니크 제약으로 저장이 실패한다.
        attendanceRepository.saveAndFlush(
            AttendanceEntity(roomId = roomId, memberId = participantId, status = AttendanceStatus.ATTENDED, recorderMemberId = hostId, recordedAt = now),
        )

        assertThatThrownBy { manager.complete(RoomProgressCompletionCommand(roomId, hostId, finalAttendances(), now)) }

        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.CONFIRMED)
        assertThat(logRepository.countByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.COMPLETED)).isZero()
        assertThat(attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)).hasSize(1)
        assertThat(completedOutboxCount()).isZero()
    }

    private fun completedOutboxCount() = outboxRepository.findAll()
        .count { it.eventType == EventType.ROOM_COMPLETED.name && roomId.toString() in it.payload }

    private fun finalAttendances() = listOf(
        Attendance(hostId, AttendanceStatus.ATTENDED),
        Attendance(participantId, AttendanceStatus.ABSENT),
    )

    private fun seedConfirmedRoom() {
        val room = RoomEntity(
            id = roomId,
            jobPostingId = 1L,
            jobRoleId = 1L,
            resumePublic = false,
            sigunguId = null,
            title = "진행 저장 테스트 룸",
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingType = MeetingType.ONLINE,
            minCapacity = 2,
            maxCapacity = 4,
            startAt = now.minusHours(1),
            durationMinutes = 60,
        ).apply { confirm() }
        roomRepository.saveAndFlush(room)
        participationRepository.saveAllAndFlush(
            listOf(
                ParticipationEntity(
                    roomId,
                    hostId,
                    ParticipationRole.HOST,
                    ParticipationStatus.JOINED,
                    joinedAt = now.minusDays(2),
                ),
                ParticipationEntity(
                    roomId,
                    participantId,
                    ParticipationRole.PARTICIPANT,
                    ParticipationStatus.JOINED,
                    joinedAt = now.minusDays(1),
                ),
            ),
        )
        logRepository.saveAndFlush(
            RoomStatusLogEntity.byMember(roomId, RoomStatus.CONFIRMED, hostId, now.minusHours(2)),
        )
    }
}
