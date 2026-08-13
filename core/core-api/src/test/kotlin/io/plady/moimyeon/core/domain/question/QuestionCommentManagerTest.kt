package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.QuestionCommentEntity
import io.plady.moimyeon.storage.db.core.QuestionCommentRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class QuestionCommentManagerTest {
    private val targetValidator = mockk<QuestionCommentTargetValidator>()
    private val commentRepository = mockk<QuestionCommentRepository>()
    private val manager = QuestionCommentManager(targetValidator, commentRepository)

    private val roomId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `원 질문에 클라이언트가 보낸 MEMO 댓글을 기록한다`() {
        val commentSlot = slot<QuestionCommentEntity>()
        givenValidTarget()
        every { commentRepository.save(capture(commentSlot)) } returns mockk {
            every { id } returns 2L
        }

        val result = manager.record(
            roomId,
            targetMemberId,
            1L,
            authorMemberId,
            QuestionCommentType.MEMO,
            "메모",
        )

        assertThat(result).isEqualTo(2L)
        assertThat(commentSlot.captured.commentType).isEqualTo(QuestionCommentType.MEMO)
        assertThat(commentSlot.captured.content).isEqualTo("메모")
    }

    @Test
    fun `댓글 대상 검증에 실패하면 댓글을 기록하지 않는다`() {
        every {
            targetValidator.validate(roomId, targetMemberId, 1L)
        } throws CoreException(CoreErrorType.QUESTION_NOT_FOUND)

        assertFails(CoreErrorType.QUESTION_NOT_FOUND) {
            manager.record(
                roomId,
                targetMemberId,
                1L,
                authorMemberId,
                QuestionCommentType.MEMO,
                "꼬리질문 메모",
            )
        }

        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `MEMO에서 좋아요를 누르면 GOOD_POINT가 된다`() {
        val comment = comment()
        givenValidTarget()
        every { commentRepository.findForUpdateByIdAndDeletedAtIsNull(2L) } returns comment

        manager.toggleType(
            roomId,
            targetMemberId,
            1L,
            2L,
            authorMemberId,
            QuestionCommentType.GOOD_POINT,
        )

        assertThat(comment.commentType).isEqualTo(QuestionCommentType.GOOD_POINT)
    }

    @Test
    fun `선택된 좋아요를 다시 누르면 MEMO로 돌아간다`() {
        val comment = comment(type = QuestionCommentType.GOOD_POINT)
        givenValidTarget()
        every { commentRepository.findForUpdateByIdAndDeletedAtIsNull(2L) } returns comment

        manager.toggleType(
            roomId,
            targetMemberId,
            1L,
            2L,
            authorMemberId,
            QuestionCommentType.GOOD_POINT,
        )

        assertThat(comment.commentType).isEqualTo(QuestionCommentType.MEMO)
    }

    @Test
    fun `선택된 싫어요를 다시 누르면 MEMO로 돌아간다`() {
        val comment = comment(type = QuestionCommentType.IMPROVEMENT_POINT)
        givenValidTarget()
        every { commentRepository.findForUpdateByIdAndDeletedAtIsNull(2L) } returns comment

        manager.toggleType(
            roomId,
            targetMemberId,
            1L,
            2L,
            authorMemberId,
            QuestionCommentType.IMPROVEMENT_POINT,
        )

        assertThat(comment.commentType).isEqualTo(QuestionCommentType.MEMO)
    }

    @Test
    fun `좋아요 상태에서 싫어요를 누르면 IMPROVEMENT_POINT가 된다`() {
        val comment = comment(type = QuestionCommentType.GOOD_POINT)
        givenValidTarget()
        every { commentRepository.findForUpdateByIdAndDeletedAtIsNull(2L) } returns comment

        manager.toggleType(
            roomId,
            targetMemberId,
            1L,
            2L,
            authorMemberId,
            QuestionCommentType.IMPROVEMENT_POINT,
        )

        assertThat(comment.commentType).isEqualTo(QuestionCommentType.IMPROVEMENT_POINT)
    }

    @Test
    fun `작성자는 댓글 본문을 변경하고 삭제한다`() {
        val comment = comment()
        givenValidTarget()
        every { commentRepository.findForUpdateByIdAndDeletedAtIsNull(2L) } returns comment

        manager.edit(roomId, targetMemberId, 1L, 2L, authorMemberId, "수정한 댓글")
        manager.remove(
            roomId,
            targetMemberId,
            1L,
            2L,
            authorMemberId,
            LocalDateTime.of(2026, 8, 13, 22, 0),
        )

        assertThat(comment.content).isEqualTo("수정한 댓글")
        assertThat(comment.isDeleted()).isTrue()
    }

    @Test
    fun `다른 작성자의 댓글은 변경하지 않고 E1511 을 던진다`() {
        val comment = comment(authorMemberId = UUID.randomUUID())
        givenValidTarget()
        every { commentRepository.findForUpdateByIdAndDeletedAtIsNull(2L) } returns comment

        assertFails(CoreErrorType.QUESTION_COMMENT_NOT_FOUND) {
            manager.edit(roomId, targetMemberId, 1L, 2L, authorMemberId, "가로채기")
        }

        assertThat(comment.content).isEqualTo("원문")
    }

    private fun givenValidTarget() {
        justRun { targetValidator.validate(roomId, targetMemberId, 1L) }
    }

    private fun comment(
        authorMemberId: UUID = this.authorMemberId,
        type: QuestionCommentType = QuestionCommentType.MEMO,
    ): QuestionCommentEntity {
        return QuestionCommentEntity(
            questionId = 1L,
            authorMemberId = authorMemberId,
            commentType = type,
            content = "원문",
        )
    }

    private fun assertFails(errorType: CoreErrorType, block: () -> Unit) {
        assertThatThrownBy(block)
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
