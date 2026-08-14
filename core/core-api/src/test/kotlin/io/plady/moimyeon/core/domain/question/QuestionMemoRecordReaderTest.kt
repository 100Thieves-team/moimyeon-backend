package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.storage.db.core.QuestionCommentEntity
import io.plady.moimyeon.storage.db.core.QuestionCommentRepository
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class QuestionMemoRecordReaderTest {
    private val questionRepository = mockk<QuestionRepository>()
    private val commentRepository = mockk<QuestionCommentRepository>()
    private val reader = QuestionMemoRecordReader(questionRepository, commentRepository)
    private val roomId = UUID.randomUUID()
    private val intervieweeMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `사용한 질문 중 선택한 라운드에 작성자가 메모한 질문만 묶는다`() {
        val question = mockk<QuestionEntity>()
        val questionWithoutMyComment = mockk<QuestionEntity>()
        val comment = mockk<QuestionCommentEntity>()
        every { question.id } returns 11L
        every { question.content } returns "장애 원인을 어떻게 좁혔나요?"
        every { questionWithoutMyComment.id } returns 12L
        every { questionWithoutMyComment.content } returns "메모하지 않은 질문"
        every {
            questionRepository.findByRoomIdAndTargetMemberIdAndAskedTrueAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                intervieweeMemberId,
            )
        } returns listOf(question, questionWithoutMyComment)
        every {
            commentRepository.findAllByQuestionIdInAndAuthorMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                listOf(11L, 12L),
                authorMemberId,
            )
        } returns listOf(comment)
        every { comment.id } returns 21L
        every { comment.questionId } returns 11L
        every { comment.commentType } returns QuestionCommentType.GOOD_POINT
        every { comment.content } returns "원인을 단계적으로 좁힌 점이 좋아요"
        every { comment.createdAt } returns LocalDateTime.of(2026, 8, 14, 10, 0)

        val result = reader.getAskedRecordsByAuthor(roomId, intervieweeMemberId, authorMemberId)

        assertThat(result).containsExactly(
            QuestionMemoRecord(
                questionId = 11L,
                questionContent = "장애 원인을 어떻게 좁혔나요?",
                comments = listOf(
                    QuestionMemoComment(
                        id = 21L,
                        type = QuestionCommentType.GOOD_POINT,
                        content = "원인을 단계적으로 좁힌 점이 좋아요",
                        createdAt = LocalDateTime.of(2026, 8, 14, 10, 0),
                    ),
                ),
            ),
        )
    }
}
