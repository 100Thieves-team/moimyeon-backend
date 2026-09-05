package io.plady.moimyeon.core.domain.closing

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ClosingResponseRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

// 전원 클로징 제출 → COMPLETED 전이(MOI-469)만 본다. 제출 자체의 저장 규칙은 ClosingPersistenceIT 소관.
// 질문을 시드하지 않으므로 빈 평가 제출이 질문 집합 대조를 통과한다.
class RoomCompletedTransitionIT(
    private val submissionManager: ClosingSubmissionManager,
    private val roomRepository: RoomRepository,
    private val attendanceRepository: AttendanceRepository,
    private val closingResponseRepository: ClosingResponseRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val entityManager: EntityManager,
    transactionManager: PlatformTransactionManager,
) : ContextTest() {
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val roomId = UUID.randomUUID()
    private val firstMemberId = UUID.randomUUID()
    private val secondMemberId = UUID.randomUUID()
    private val absentMemberId = UUID.randomUUID()
    private val startedAt = LocalDateTime.of(2026, 8, 13, 3, 0)

    @AfterEach
    fun cleanUp() {
        closingResponseRepository.deleteAll(closingResponseRepository.findAll().filter { it.roomId == roomId })
        attendanceRepository.deleteAll(attendanceRepository.findAll().filter { it.roomId == roomId })
        roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId == roomId })
        if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
    }

    @Test
    fun `일부만 제출한 동안에는 룸이 IN_PROGRESS 로 남는다`() {
        seedInProgressRoom()

        submissionManager.submit(command(firstMemberId))

        assertThat(roomStatus()).isEqualTo(RoomStatus.IN_PROGRESS)
        assertThat(completedLog()).isNull()
    }

    @Test
    fun `출석자 전원이 클로징을 제출하면 룸이 COMPLETED 가 되고 전이 로그가 남는다`() {
        seedInProgressRoom()

        submissionManager.submit(command(firstMemberId))
        val lastSubmission = submissionManager.submit(command(secondMemberId))

        assertThat(roomStatus()).isEqualTo(RoomStatus.COMPLETED)
        val log = completedLog() ?: error("COMPLETED 전이 로그가 남지 않음")
        assertThat(log.handlerMemberId).isEqualTo(secondMemberId)
        assertThat(log.occurredAt).isEqualTo(lastSubmission.submittedAt)
    }

    private fun command(memberId: UUID): ClosingSubmissionCommand {
        return ClosingSubmissionCommand(roomId = roomId, memberId = memberId, evaluations = emptyList())
    }

    private fun roomStatus(): RoomStatus = roomRepository.findById(roomId).orElseThrow().status

    private fun completedLog() = roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(
        roomId,
        RoomStatus.COMPLETED,
    )

    // 출석 2명 + 불참 1명. 불참자의 미제출이 전이를 막지 않는 것까지 함께 본다.
    private fun seedInProgressRoom() {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "완료 전이 테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 4,
                startAt = startedAt.minusHours(1),
                durationMinutes = 60,
            ),
        )
        attendanceRepository.saveAllAndFlush(
            listOf(
                attendance(firstMemberId, AttendanceStatus.ATTENDED),
                attendance(secondMemberId, AttendanceStatus.ATTENDED),
                attendance(absentMemberId, AttendanceStatus.ABSENT),
            ),
        )
        transactionTemplate.executeWithoutResult {
            entityManager.createNativeQuery("update room set status = 'IN_PROGRESS' where id = :roomId")
                .setParameter("roomId", roomId)
                .executeUpdate()
            entityManager.clear()
        }
    }

    private fun attendance(memberId: UUID, status: AttendanceStatus): AttendanceEntity {
        return AttendanceEntity(
            roomId = roomId,
            memberId = memberId,
            status = status,
            recorderMemberId = firstMemberId,
            recordedAt = startedAt.minusHours(1),
        )
    }
}
