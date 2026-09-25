package io.plady.moimyeon.core.domain.progress

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.AttendanceRepository
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
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()
    private val participantId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 8, 11, 1, 0)

    @AfterEach
    fun cleanUp() {
        logRepository.deleteAll(logRepository.findAll().filter { it.roomId == roomId })
        attendanceRepository.deleteAll(attendanceRepository.findAll().filter { it.roomId == roomId })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
    }

    @Test
    fun `완료와 출석 기록은 별도 단계로 저장된다`() {
        seedConfirmedRoom()

        manager.complete(RoomProgressCompletionCommand(roomId, hostId, now))

        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.COMPLETED)
        assertThat(attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)).isEmpty()

        manager.recordAttendances(
            RoomAttendanceRecordCommand(roomId, hostId, finalAttendances(), now.plusMinutes(1)),
        )

        assertThat(reader.getAttendance(roomId, hostId)).isEqualTo(Attendance(hostId, AttendanceStatus.ATTENDED))
        assertThat(reader.getAttendance(roomId, participantId)).isEqualTo(Attendance(participantId, AttendanceStatus.ABSENT))
    }

    @Test
    fun `출석은 최신 확정 참여자 전원과 정확히 일치해야 하고 한 번만 기록한다`() {
        seedConfirmedRoom()
        manager.complete(RoomProgressCompletionCommand(roomId, hostId, now))

        assertThatThrownBy {
            manager.recordAttendances(
                RoomAttendanceRecordCommand(roomId, hostId, listOf(Attendance(hostId, AttendanceStatus.ATTENDED)), now),
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_PARTICIPANT_MISMATCH)
        }

        manager.recordAttendances(RoomAttendanceRecordCommand(roomId, hostId, finalAttendances(), now))
        assertThatThrownBy {
            manager.recordAttendances(RoomAttendanceRecordCommand(roomId, hostId, finalAttendances(), now.plusMinutes(1)))
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_ATTENDANCE_ALREADY_RECORDED)
        }
    }

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
