package io.plady.moimyeon.core.domain.closing

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.QuestionVote
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ClosingServiceTest {
    private val submissionManager = mockk<ClosingSubmissionManager>()
    private val accessValidator = mockk<ClosingAccessValidator>()
    private val questionReader = mockk<ClosingQuestionReader>()
    private val service = ClosingService(submissionManager, accessValidator, questionReader)

    private val roomId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val submittedAt = LocalDateTime.of(2026, 8, 13, 3, 0, 0, 123_000_000)

    @Test
    fun `출석 참여자가 질문별 평가를 제출하면 클로징 제출 시각을 돌려받는다`() {
        val evaluations = mutableListOf(
            QuestionEvaluation(1L, QuestionVote.MEMORABLE),
            QuestionEvaluation(2L, QuestionVote.DISAPPOINTING),
        )
        val command = ClosingSubmissionCommand(
            roomId = roomId,
            memberId = memberId,
            evaluations = evaluations.toList(),
        )
        val submission = ClosingSubmission(roomId, memberId, submittedAt)
        every { submissionManager.submit(command) } returns submission

        val result = service.submit(memberId, roomId, evaluations)
        evaluations.clear()

        assertThat(result).isEqualTo(submission)
        verify(exactly = 1) { submissionManager.submit(command) }
    }

    @Test
    fun `불참자는 질문 평가와 클로징을 제출할 수 없다`() {
        val evaluations = listOf(QuestionEvaluation(1L, QuestionVote.MEMORABLE))
        every {
            submissionManager.submit(ClosingSubmissionCommand(roomId, memberId, evaluations))
        } throws CoreException(CoreErrorType.CLOSING_SUBMISSION_FORBIDDEN)

        assertThatThrownBy { service.submit(memberId, roomId, evaluations) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.CLOSING_SUBMISSION_FORBIDDEN)
            }
    }

    @Test
    fun `출석 참여자는 클로징에서 평가할 실제 사용 원 질문을 조회한다`() {
        val questions = listOf(
            ClosingQuestion(1L, UUID.randomUUID(), "장애 원인을 어떻게 좁혔나요?", io.plady.moimyeon.core.enums.QuestionSource.PREPARATION),
            ClosingQuestion(2L, UUID.randomUUID(), "복구 순서는 어떻게 정했나요?", io.plady.moimyeon.core.enums.QuestionSource.IN_PROGRESS),
        )
        every { accessValidator.validateParticipant(roomId, memberId) } returns Unit
        every { questionReader.getQuestions(roomId, memberId) } returns questions

        assertThat(service.getQuestions(memberId, roomId)).containsExactlyElementsOf(questions)
        verify(exactly = 1) { accessValidator.validateParticipant(roomId, memberId) }
        verify(exactly = 1) { questionReader.getQuestions(roomId, memberId) }
    }
}
