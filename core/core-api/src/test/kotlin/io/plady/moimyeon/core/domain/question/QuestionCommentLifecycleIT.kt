package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class QuestionCommentLifecycleIT(
    private val questionCommentService: QuestionCommentService,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val attendanceRepository: AttendanceRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val questionRepository: QuestionRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val hostMemberId = UUID.randomUUID()
    private val participantMemberId = UUID.randomUUID()
    private val firstIntervieweeMemberId = UUID.randomUUID()
    private val secondIntervieweeMemberId = UUID.randomUUID()
    private val confirmedAt = LocalDateTime.of(2026, 8, 13, 10, 0)
    private val startedAt = confirmedAt.plusHours(1)

    @Test
    fun `원질문 댓글은 진행 중 작성 관리되고 종료 후 확정 참여자 모두에게 공개된다`() {
        seedInProgressRoom()
        val rootQuestion = persistQuestion(firstIntervieweeMemberId, "정합성을 어떻게 복구했나요?")
        val followUpQuestion = persistQuestion(
            targetMemberId = firstIntervieweeMemberId,
            content = "배치 주기는 어떻게 정했나요?",
            parentQuestionId = rootQuestion.id,
        )
        val otherRoundQuestion = persistQuestion(secondIntervieweeMemberId, "쿠폰 시스템을 설계해 주세요")

        val absentParticipantCommentId = questionCommentService.leaveComment(
            participantMemberId,
            roomId,
            firstIntervieweeMemberId,
            rootQuestion.id,
            QuestionCommentType.MEMO,
            "장애 시나리오부터 짚고 시작했어요",
        )
        questionCommentService.toggleType(
            participantMemberId,
            roomId,
            firstIntervieweeMemberId,
            rootQuestion.id,
            absentParticipantCommentId,
            QuestionCommentType.GOOD_POINT,
        )
        questionCommentService.editComment(
            participantMemberId,
            roomId,
            firstIntervieweeMemberId,
            rootQuestion.id,
            absentParticipantCommentId,
            "장애 시나리오부터 짚고 시작해서 좋았어요",
        )
        val deletedCommentId = questionCommentService.leaveComment(
            hostMemberId,
            roomId,
            firstIntervieweeMemberId,
            rootQuestion.id,
            QuestionCommentType.MEMO,
            "삭제할 메모",
        )
        questionCommentService.deleteComment(
            hostMemberId,
            roomId,
            firstIntervieweeMemberId,
            rootQuestion.id,
            deletedCommentId,
        )

        repeat(20) { index ->
            questionCommentService.leaveComment(
                hostMemberId,
                roomId,
                firstIntervieweeMemberId,
                rootQuestion.id,
                QuestionCommentType.MEMO,
                "페이지네이션 메모 ${index + 1}",
            )
        }
        questionCommentService.leaveComment(
            hostMemberId,
            roomId,
            secondIntervieweeMemberId,
            otherRoundQuestion.id,
            QuestionCommentType.MEMO,
            "다른 라운드 메모",
        )
        entityManager.flush()
        entityManager.clear()

        val firstPage = questionCommentService.getComments(
            hostMemberId,
            roomId,
            firstIntervieweeMemberId,
            rootQuestion.id,
        )
        val secondPage = questionCommentService.getComments(
            participantMemberId,
            roomId,
            firstIntervieweeMemberId,
            rootQuestion.id,
            checkNotNull(firstPage.nextCursor),
        )

        assertThat(firstPage.comments).hasSize(20)
        assertThat(secondPage.comments).hasSize(1)
        assertThat(secondPage.nextCursor).isNull()
        val allComments = firstPage.comments + secondPage.comments
        val firstComment = allComments.first()
        assertThat(firstComment.authorMemberId).isEqualTo(participantMemberId)
        assertThat(firstComment.type).isEqualTo(QuestionCommentType.GOOD_POINT)
        assertThat(firstComment.content).isEqualTo("장애 시나리오부터 짚고 시작해서 좋았어요")
        assertThat(allComments.map { it.content }).doesNotContain(
            "삭제할 메모",
            "다른 라운드 메모",
        )

        assertThatThrownBy {
            questionCommentService.getComments(
                firstIntervieweeMemberId,
                roomId,
                firstIntervieweeMemberId,
                rootQuestion.id,
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_COMMENT_FORBIDDEN)
        }
        assertThatThrownBy {
            questionCommentService.leaveComment(
                hostMemberId,
                roomId,
                firstIntervieweeMemberId,
                followUpQuestion.id,
                QuestionCommentType.MEMO,
                "꼬리질문에는 저장되지 않아야 해요",
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_NOT_FOUND)
        }

        completeRoom()

        val intervieweeView = questionCommentService.getComments(
            firstIntervieweeMemberId,
            roomId,
            firstIntervieweeMemberId,
            rootQuestion.id,
        )
        assertThat(intervieweeView.comments).hasSize(20)
        assertThat(intervieweeView.nextCursor).isNotNull()
        assertThatThrownBy {
            questionCommentService.editComment(
                participantMemberId,
                roomId,
                firstIntervieweeMemberId,
                rootQuestion.id,
                absentParticipantCommentId,
                "종료 후 수정",
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_COMMENT_NOT_EDITABLE)
        }
    }

    private fun completeRoom() {
        entityManager.createQuery(
            "UPDATE RoomEntity r SET r.status = :status WHERE r.id = :roomId",
        ).setParameter("status", RoomStatus.COMPLETED)
            .setParameter("roomId", roomId)
            .executeUpdate()
        entityManager.clear()
    }

    private fun seedInProgressRoom() {
        val room = RoomEntity(
            id = roomId,
            jobPostingId = 1L,
            jobRoleId = 1L,
            resumePublic = false,
            sigunguId = null,
            title = "질문 댓글 테스트 룸",
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
                participation(hostMemberId, ParticipationRole.HOST, confirmedAt.minusDays(4)),
                participation(participantMemberId, ParticipationRole.PARTICIPANT, confirmedAt.minusDays(3)),
                participation(firstIntervieweeMemberId, ParticipationRole.PARTICIPANT, confirmedAt.minusDays(2)),
                participation(secondIntervieweeMemberId, ParticipationRole.PARTICIPANT, confirmedAt.minusDays(1)),
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
                attendance(hostMemberId),
                attendance(participantMemberId, AttendanceStatus.ABSENT),
                attendance(firstIntervieweeMemberId),
                attendance(secondIntervieweeMemberId),
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

    private fun attendance(
        memberId: UUID,
        status: AttendanceStatus = AttendanceStatus.ATTENDED,
    ): AttendanceEntity {
        return AttendanceEntity(
            roomId = roomId,
            memberId = memberId,
            status = status,
            recorderMemberId = hostMemberId,
            recordedAt = startedAt,
        )
    }

    private fun persistQuestion(
        targetMemberId: UUID,
        content: String,
        parentQuestionId: Long? = null,
    ): QuestionEntity {
        return questionRepository.saveAndFlush(
            QuestionEntity(
                roomId = roomId,
                targetMemberId = targetMemberId,
                authorMemberId = hostMemberId,
                parentQuestionId = parentQuestionId,
                content = content,
                source = QuestionSource.PREPARATION,
            ),
        )
    }
}
