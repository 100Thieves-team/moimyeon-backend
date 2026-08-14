package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class QuestionPreparationServiceTest {
    private val accessValidator = mockk<QuestionPreparationAccessValidator>()
    private val questionRecorder = mockk<QuestionRecorder>()
    private val clock = Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneOffset.UTC)
    private val service = QuestionPreparationService(accessValidator, questionRecorder, clock)

    private val roomId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val questionContent = "프로젝트에서 가장 어려웠던 기술적 결정은 무엇이었나요?"
    private val followUpContent = "다시 선택한다면 같은 결정을 하시겠어요?"
    private val now = LocalDateTime.of(2026, 8, 10, 3, 0)

    @Test
    fun `확정 참여자는 역할 배정 없이 다른 확정 참여자에게 준비 질문을 남긴다`() {
        givenQuestionCanBePrepared()
        every {
            questionRecorder.record(
                roomId,
                targetMemberId,
                authorMemberId,
                null,
                questionContent,
                QuestionSource.PREPARATION,
            )
        } returns 1L

        val questionId = service.leaveQuestion(authorMemberId, roomId, targetMemberId, questionContent)

        assertThat(questionId).isEqualTo(1L)
        verifyOrder {
            accessValidator.validateAuthor(roomId, authorMemberId)
            accessValidator.validateTarget(roomId, authorMemberId, targetMemberId)
            questionRecorder.record(
                roomId,
                targetMemberId,
                authorMemberId,
                null,
                questionContent,
                QuestionSource.PREPARATION,
            )
        }
    }

    @Test
    fun `준비 단계에서 원 질문에 꼬리질문을 남기면 준비 출처로 저장한다`() {
        val parentQuestionId = 1L
        justRun { accessValidator.validateAuthor(roomId, authorMemberId) }
        every {
            questionRecorder.recordFollowUp(
                roomId,
                authorMemberId,
                parentQuestionId,
                followUpContent,
                QuestionSource.PREPARATION,
            )
        } returns 2L

        val followUpQuestionId = service.leaveFollowUp(
            authorMemberId,
            roomId,
            parentQuestionId,
            followUpContent,
        )

        assertThat(followUpQuestionId).isEqualTo(2L)
        verifyOrder {
            accessValidator.validateAuthor(roomId, authorMemberId)
            questionRecorder.recordFollowUp(
                roomId,
                authorMemberId,
                parentQuestionId,
                followUpContent,
                QuestionSource.PREPARATION,
            )
        }
    }

    @Test
    fun `룸이 확정되지 않았으면 대상 자격을 확인하거나 질문을 저장하지 않고 E1504 를 던진다`() {
        every {
            accessValidator.validateAuthor(roomId, authorMemberId)
        } throws CoreException(CoreErrorType.QUESTION_PREPARATION_NOT_OPEN)

        assertPreparationFails(CoreErrorType.QUESTION_PREPARATION_NOT_OPEN) {
            service.leaveQuestion(authorMemberId, roomId, targetMemberId, questionContent)
        }

        verify(exactly = 0) { accessValidator.validateTarget(any(), any(), any()) }
        verify(exactly = 0) { questionRecorder.record(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `현재 참여자가 아니면 대상 자격을 확인하거나 질문을 저장하지 않고 E1505 를 던진다`() {
        every {
            accessValidator.validateAuthor(roomId, authorMemberId)
        } throws CoreException(CoreErrorType.QUESTION_PREPARATION_FORBIDDEN)

        assertPreparationFails(CoreErrorType.QUESTION_PREPARATION_FORBIDDEN) {
            service.leaveQuestion(authorMemberId, roomId, targetMemberId, questionContent)
        }

        verify(exactly = 0) { accessValidator.validateTarget(any(), any(), any()) }
        verify(exactly = 0) { questionRecorder.record(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `질문 대상이 확정 참여자가 아니면 질문을 저장하지 않고 E1506 을 던진다`() {
        justRun { accessValidator.validateAuthor(roomId, authorMemberId) }
        every {
            accessValidator.validateTarget(roomId, authorMemberId, targetMemberId)
        } throws CoreException(CoreErrorType.QUESTION_TARGET_NOT_FOUND)

        assertPreparationFails(CoreErrorType.QUESTION_TARGET_NOT_FOUND) {
            service.leaveQuestion(authorMemberId, roomId, targetMemberId, questionContent)
        }

        verify(exactly = 0) { questionRecorder.record(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `다른 룸의 원 질문에는 꼬리질문을 저장하지 않고 E1507 을 던진다`() {
        val parentQuestionId = 1L
        justRun { accessValidator.validateAuthor(roomId, authorMemberId) }
        every {
            questionRecorder.recordFollowUp(
                roomId,
                authorMemberId,
                parentQuestionId,
                followUpContent,
                QuestionSource.PREPARATION,
            )
        } throws CoreException(CoreErrorType.QUESTION_NOT_FOUND)

        assertPreparationFails(CoreErrorType.QUESTION_NOT_FOUND) {
            service.leaveFollowUp(
                authorMemberId,
                roomId,
                parentQuestionId,
                followUpContent,
            )
        }
    }

    @Test
    fun `질문을 남긴 작성자는 자신의 질문을 삭제한다`() {
        justRun { accessValidator.validateAuthor(roomId, authorMemberId) }
        justRun { questionRecorder.removeOwnedBy(roomId, 1L, authorMemberId, now) }

        service.deleteQuestion(authorMemberId, roomId, 1L)

        verifyOrder {
            accessValidator.validateAuthor(roomId, authorMemberId)
            questionRecorder.removeOwnedBy(roomId, 1L, authorMemberId, now)
        }
    }

    @Test
    fun `현재 참여자가 아니면 질문을 삭제하지 않고 E1505 를 던진다`() {
        every {
            accessValidator.validateAuthor(roomId, authorMemberId)
        } throws CoreException(CoreErrorType.QUESTION_PREPARATION_FORBIDDEN)

        assertPreparationFails(CoreErrorType.QUESTION_PREPARATION_FORBIDDEN) {
            service.deleteQuestion(authorMemberId, roomId, 1L)
        }

        verify(exactly = 0) { questionRecorder.removeOwnedBy(any(), any(), any(), any()) }
    }

    @Test
    fun `다른 참여자가 질문을 삭제하면 저장 상태를 바꾸지 않고 E1505 를 던진다`() {
        justRun { accessValidator.validateAuthor(roomId, authorMemberId) }
        every {
            questionRecorder.removeOwnedBy(roomId, 1L, authorMemberId, now)
        } throws CoreException(CoreErrorType.QUESTION_PREPARATION_FORBIDDEN)

        assertPreparationFails(CoreErrorType.QUESTION_PREPARATION_FORBIDDEN) {
            service.deleteQuestion(authorMemberId, roomId, 1L)
        }
    }

    @Test
    fun `작성자는 질문을 삭제한 뒤 같은 대상에게 새 질문을 다시 남길 수 있다`() {
        justRun { accessValidator.validateAuthor(roomId, authorMemberId) }
        justRun { questionRecorder.removeOwnedBy(roomId, 1L, authorMemberId, now) }
        givenQuestionCanBePrepared()
        every {
            questionRecorder.record(
                roomId,
                targetMemberId,
                authorMemberId,
                null,
                questionContent,
                QuestionSource.PREPARATION,
            )
        } returns 2L

        service.deleteQuestion(authorMemberId, roomId, 1L)
        val rewrittenQuestionId = service.leaveQuestion(
            authorMemberId,
            roomId,
            targetMemberId,
            questionContent,
        )

        assertThat(rewrittenQuestionId).isEqualTo(2L)
        verifyOrder {
            accessValidator.validateAuthor(roomId, authorMemberId)
            questionRecorder.removeOwnedBy(roomId, 1L, authorMemberId, now)
            accessValidator.validateAuthor(roomId, authorMemberId)
            accessValidator.validateTarget(roomId, authorMemberId, targetMemberId)
            questionRecorder.record(
                roomId,
                targetMemberId,
                authorMemberId,
                null,
                questionContent,
                QuestionSource.PREPARATION,
            )
        }
    }

    private fun givenQuestionCanBePrepared() {
        justRun { accessValidator.validateAuthor(roomId, authorMemberId) }
        justRun { accessValidator.validateTarget(roomId, authorMemberId, targetMemberId) }
    }

    private fun assertPreparationFails(errorType: CoreErrorType, block: () -> Unit) {
        assertThatThrownBy(block)
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
