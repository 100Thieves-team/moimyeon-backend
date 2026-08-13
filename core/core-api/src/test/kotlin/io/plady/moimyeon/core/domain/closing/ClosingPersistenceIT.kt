package io.plady.moimyeon.core.domain.closing

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.enums.QuestionVote
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ClosingResponseRepository
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

class ClosingPersistenceIT(
    private val submissionManager: ClosingSubmissionManager,
    private val roomRepository: RoomRepository,
    private val attendanceRepository: AttendanceRepository,
    private val questionRepository: QuestionRepository,
    private val closingResponseRepository: ClosingResponseRepository,
    private val entityManager: EntityManager,
    transactionManager: PlatformTransactionManager,
) : ContextTest() {
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val roomId = UUID.randomUUID()
    private val firstMemberId = UUID.randomUUID()
    private val secondMemberId = UUID.randomUUID()
    private val submittedAt = LocalDateTime.of(2026, 8, 13, 3, 0)

    @AfterEach
    fun cleanUp() {
        closingResponseRepository.deleteAll(closingResponseRepository.findAll().filter { it.roomId == roomId })
        attendanceRepository.deleteAll(attendanceRepository.findAll().filter { it.roomId == roomId })
        questionRepository.deleteAll(questionRepository.findAll().filter { it.roomId == roomId })
        if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
    }

    @Test
    fun `자신의 면접 라운드에서 실제로 받은 원 질문 평가만 한 번에 저장한다`() {
        seedInProgressRoomAndAttendances()
        val questions = seedQuestionsForEachInterviewee()
        val command = command(
            firstMemberId,
            questions.firstMemberQuestions,
            listOf(QuestionVote.MEMORABLE, QuestionVote.DISAPPOINTING),
        )

        val first = submissionManager.submit(command)
        val repeated = submissionManager.submit(
            command.copy(
                evaluations = command.evaluations.map { it.copy(vote = QuestionVote.MEMORABLE) },
            ),
        )

        val persisted = transactionTemplate.execute {
            val response = closingResponseRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(
                roomId,
                firstMemberId,
            ) ?: error("클로징 제출이 저장되지 않음")
            PersistedClosingResponse(
                submittedAt = response.createdAt,
                questionIds = response.questionVotes().map { it.questionId },
            )
        }

        assertThat(repeated).isEqualTo(first)
        assertThat(persisted.questionIds)
            .containsExactlyInAnyOrderElementsOf(questions.firstMemberQuestions.map { it.id })
        assertThat(persisted.submittedAt).isEqualTo(first.submittedAt)
    }

    private fun command(
        memberId: UUID,
        questions: List<QuestionEntity>,
        votes: List<QuestionVote>,
    ): ClosingSubmissionCommand {
        return ClosingSubmissionCommand(
            roomId = roomId,
            memberId = memberId,
            evaluations = questions.zip(votes) { question, vote -> QuestionEvaluation(question.id, vote) },
        )
    }

    private fun seedInProgressRoomAndAttendances() {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "클로징 저장 테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 4,
                startAt = submittedAt.minusHours(1),
                durationMinutes = 60,
            ),
        )
        attendanceRepository.saveAllAndFlush(
            memberIds.map { memberId ->
                AttendanceEntity(
                    roomId = roomId,
                    memberId = memberId,
                    status = AttendanceStatus.ATTENDED,
                    recorderMemberId = firstMemberId,
                    recordedAt = submittedAt.minusHours(1),
                )
            },
        )
        transactionTemplate.executeWithoutResult {
            entityManager.createNativeQuery("update room set status = 'IN_PROGRESS' where id = :roomId")
                .setParameter("roomId", roomId)
                .executeUpdate()
            entityManager.clear()
        }
    }

    private fun seedQuestionsForEachInterviewee(): IntervieweeQuestions {
        val firstMemberQuestions = listOf(
            question("이중 결제 방지 처리", targetMemberId = firstMemberId),
            question("정합성 복구 흐름", targetMemberId = firstMemberId),
        ).onEach { it.changeAsked(true) }
        val savedFirstMemberQuestions = questionRepository.saveAllAndFlush(firstMemberQuestions)
        val secondMemberQuestion = question("캐시 무효화 전략", targetMemberId = secondMemberId)
            .also { it.changeAsked(true) }
        questionRepository.saveAndFlush(secondMemberQuestion)
        questionRepository.saveAndFlush(question("질문하지 않은 카드", targetMemberId = firstMemberId))
        questionRepository.saveAndFlush(
            question(
                "평가 대상이 아닌 꼬리질문",
                targetMemberId = firstMemberId,
                parentQuestionId = savedFirstMemberQuestions.first().id,
            ).also { it.changeAsked(true) },
        )
        return IntervieweeQuestions(savedFirstMemberQuestions)
    }

    private fun question(
        content: String,
        targetMemberId: UUID,
        parentQuestionId: Long? = null,
    ): QuestionEntity {
        return QuestionEntity(
            roomId = roomId,
            targetMemberId = targetMemberId,
            authorMemberId = secondMemberId,
            parentQuestionId = parentQuestionId,
            content = content,
            source = QuestionSource.PREPARATION,
        )
    }

    private data class IntervieweeQuestions(
        val firstMemberQuestions: List<QuestionEntity>,
    )

    private data class PersistedClosingResponse(
        val submittedAt: LocalDateTime,
        val questionIds: List<Long>,
    )

    private val memberIds = setOf(firstMemberId, secondMemberId)
}
