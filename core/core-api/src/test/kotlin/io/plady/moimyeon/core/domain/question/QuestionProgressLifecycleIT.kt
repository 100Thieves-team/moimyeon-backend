package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class QuestionProgressLifecycleIT(
    private val questionProgressService: QuestionProgressService,
    private val questionCardSetReader: QuestionCardSetReader,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val attendanceRepository: AttendanceRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val questionRepository: QuestionRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val hostMemberId = UUID.randomUUID()
    private val noShowMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val confirmedAt = LocalDateTime.of(2026, 8, 13, 10, 0)
    private val startedAt = confirmedAt.plusHours(1)

    @Test
    fun `노쇼 확정 참여자도 질문 표시와 진행 중 질문 추가를 하고 준비 카드와 함께 본다`() {
        seedInProgressRoom()
        val question = questionRepository.saveAndFlush(
            QuestionEntity(
                roomId = roomId,
                targetMemberId = targetMemberId,
                authorMemberId = hostMemberId,
                content = "장애 원인을 어떻게 좁혔나요?",
                source = QuestionSource.PREPARATION,
            ),
        )

        val inProgressQuestionId = questionProgressService.leaveQuestion(
            noShowMemberId,
            roomId,
            targetMemberId,
            "트랜잭션 복구 기준은 무엇이었나요?",
        )
        val followUpId = questionProgressService.leaveFollowUp(
            noShowMemberId,
            roomId,
            targetMemberId,
            question.id,
            "실시간 검증으로 바꾼다면 어디부터 손대나요?",
        )
        questionProgressService.changeAsked(noShowMemberId, roomId, targetMemberId, question.id, true)
        questionProgressService.changeAsked(noShowMemberId, roomId, targetMemberId, inProgressQuestionId, true)
        questionProgressService.changeAsked(noShowMemberId, roomId, targetMemberId, inProgressQuestionId, false)
        questionProgressService.changeAsked(noShowMemberId, roomId, targetMemberId, followUpId, true)
        entityManager.flush()
        entityManager.clear()

        val cards = questionCardSetReader.getByRoomAndTarget(roomId, targetMemberId).questions
        assertThat(cards.map { it.id }).containsExactly(question.id, inProgressQuestionId)
        assertThat(cards.first().asked).isTrue()
        assertThat(cards.first().source).isEqualTo(QuestionSource.PREPARATION)
        val followUp = cards.first().followUps.single()
        assertThat(followUp.id).isEqualTo(followUpId)
        assertThat(followUp.authorMemberId).isEqualTo(noShowMemberId)
        assertThat(followUp.source).isEqualTo(QuestionSource.IN_PROGRESS)
        assertThat(followUp.asked).isTrue()
        assertThat(cards.last().authorMemberId).isEqualTo(noShowMemberId)
        assertThat(cards.last().source).isEqualTo(QuestionSource.IN_PROGRESS)
        assertThat(cards.last().asked).isFalse()
    }

    private fun seedInProgressRoom() {
        val room = RoomEntity(
            id = roomId,
            jobPostingId = 1L,
            jobRoleId = 1L,
            resumePublic = false,
            sigunguId = null,
            title = "질문 사용 테스트 룸",
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingType = MeetingType.ONLINE,
            minCapacity = 3,
            maxCapacity = 4,
            startAt = startedAt,
            durationMinutes = 60,
        )
        room.confirm()
        room.startProgress(startedAt)
        roomRepository.saveAndFlush(room)

        participationRepository.saveAllAndFlush(
            listOf(
                participation(hostMemberId, ParticipationRole.HOST, confirmedAt.minusDays(3)),
                participation(noShowMemberId, ParticipationRole.PARTICIPANT, confirmedAt.minusDays(2)),
                participation(targetMemberId, ParticipationRole.PARTICIPANT, confirmedAt.minusDays(1)),
            ),
        )
        roomStatusLogRepository.saveAndFlush(
            RoomStatusLogEntity.byMember(
                roomId = roomId,
                transitionType = RoomStatus.CONFIRMED,
                handlerMemberId = hostMemberId,
                occurredAt = confirmedAt,
            ),
        )
        attendanceRepository.saveAllAndFlush(
            listOf(
                attendance(hostMemberId, AttendanceStatus.ATTENDED),
                attendance(noShowMemberId, AttendanceStatus.ABSENT),
                attendance(targetMemberId, AttendanceStatus.ATTENDED),
            ),
        )
    }

    private fun participation(
        memberId: UUID,
        role: ParticipationRole,
        joinedAt: LocalDateTime,
    ): ParticipationEntity {
        return ParticipationEntity(
            roomId = roomId,
            memberId = memberId,
            participationRole = role,
            status = ParticipationStatus.JOINED,
            joinedAt = joinedAt,
        )
    }

    private fun attendance(memberId: UUID, status: AttendanceStatus): AttendanceEntity {
        return AttendanceEntity(
            roomId = roomId,
            memberId = memberId,
            status = status,
            recorderMemberId = hostMemberId,
            recordedAt = startedAt,
        )
    }
}
