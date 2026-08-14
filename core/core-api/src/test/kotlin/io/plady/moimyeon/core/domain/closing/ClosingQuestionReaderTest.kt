package io.plady.moimyeon.core.domain.closing

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.storage.db.core.ClosingQuestionRepository
import io.plady.moimyeon.storage.db.core.QuestionEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ClosingQuestionReaderTest {
    private val questionRepository = mockk<ClosingQuestionRepository>()
    private val reader = ClosingQuestionReader(questionRepository)

    private val roomId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()

    @Test
    fun `실제로 사용된 원 질문을 클로징 질문으로 변환한다`() {
        val question = mockk<QuestionEntity> {
            every { id } returns 11L
            every { authorMemberId } returns memberId
            every { content } returns "정합성을 어떻게 복구했나요?"
            every { source } returns QuestionSource.PREPARATION
        }
        every { questionRepository.findAllAskedTopLevelByRoomIdAndTargetMemberId(roomId, memberId) } returns
            listOf(question)

        assertThat(reader.getQuestions(roomId, memberId)).containsExactly(
            ClosingQuestion(11L, memberId, "정합성을 어떻게 복구했나요?", QuestionSource.PREPARATION),
        )
    }
}
