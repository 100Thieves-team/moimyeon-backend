package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class QuestionUsageMarkerTest {
    private val questionRepository = mockk<QuestionRepository>()
    private val marker = QuestionUsageMarker(questionRepository)

    private val roomId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()

    @Test
    fun `선택한 대상의 원 질문을 질문했음으로 표시한다`() {
        val question = question()
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 1L)
        } returns question

        marker.markAsked(roomId, targetMemberId, 1L)

        assertThat(question.asked).isTrue()
    }

    @Test
    fun `꼬리질문도 원 질문과 같은 방식으로 질문했음으로 표시한다`() {
        val followUp = question(parentQuestionId = 1L)
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 2L)
        } returns followUp

        marker.markAsked(roomId, targetMemberId, 2L)

        assertThat(followUp.asked).isTrue()
    }

    @Test
    fun `이미 표시된 질문을 다시 표시해도 질문함 상태를 유지한다`() {
        val question = question(asked = true)
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 1L)
        } returns question

        marker.markAsked(roomId, targetMemberId, 1L)

        assertThat(question.asked).isTrue()
    }

    @Test
    fun `질문이 선택한 대상의 카드가 아니면 상태를 유지하고 E1507 을 던진다`() {
        val question = question(targetMemberId = UUID.randomUUID())
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 1L)
        } returns question

        assertThatThrownBy {
            marker.markAsked(roomId, targetMemberId, 1L)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_NOT_FOUND)
        }
        assertThat(question.asked).isFalse()
    }

    @Test
    fun `활성 질문을 찾을 수 없으면 E1507 을 던진다`() {
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 1L)
        } returns null

        assertThatThrownBy {
            marker.markAsked(roomId, targetMemberId, 1L)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_NOT_FOUND)
        }
    }

    private fun question(
        targetMemberId: UUID = this.targetMemberId,
        parentQuestionId: Long? = null,
        asked: Boolean = false,
    ): QuestionEntity {
        return QuestionEntity(
            roomId = roomId,
            targetMemberId = targetMemberId,
            authorMemberId = UUID.randomUUID(),
            parentQuestionId = parentQuestionId,
            content = "질문",
            source = QuestionSource.PREPARATION,
            asked = asked,
        )
    }
}
