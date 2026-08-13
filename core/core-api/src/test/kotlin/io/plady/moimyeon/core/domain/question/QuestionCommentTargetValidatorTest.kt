package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class QuestionCommentTargetValidatorTest {
    private val questionRepository = mockk<QuestionRepository>()
    private val validator = QuestionCommentTargetValidator(questionRepository)

    private val roomId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()

    @Test
    fun `해당 룸과 면접자의 원질문은 댓글 대상으로 인정한다`() {
        every { questionRepository.findByIdAndDeletedAtIsNull(1L) } returns question()

        assertThatCode {
            validator.validate(roomId, targetMemberId, 1L)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `질문이 없으면 E1507 을 던진다`() {
        every { questionRepository.findByIdAndDeletedAtIsNull(1L) } returns null

        assertFails {
            validator.validate(roomId, targetMemberId, 1L)
        }
    }

    @Test
    fun `다른 룸과 면접자의 질문이나 꼬리질문은 E1507 을 던진다`() {
        listOf(
            question(roomId = UUID.randomUUID()),
            question(targetMemberId = UUID.randomUUID()),
            question(parentQuestionId = 9L),
        ).forEach { invalidQuestion ->
            every { questionRepository.findByIdAndDeletedAtIsNull(1L) } returns invalidQuestion

            assertFails {
                validator.validate(roomId, targetMemberId, 1L)
            }
        }
    }

    private fun question(
        roomId: UUID = this.roomId,
        targetMemberId: UUID = this.targetMemberId,
        parentQuestionId: Long? = null,
    ): QuestionEntity {
        return QuestionEntity(
            roomId = roomId,
            targetMemberId = targetMemberId,
            authorMemberId = UUID.randomUUID(),
            parentQuestionId = parentQuestionId,
            content = "질문",
            source = QuestionSource.PREPARATION,
        )
    }

    private fun assertFails(block: () -> Unit) {
        assertThatThrownBy(block)
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_NOT_FOUND)
            }
    }
}
