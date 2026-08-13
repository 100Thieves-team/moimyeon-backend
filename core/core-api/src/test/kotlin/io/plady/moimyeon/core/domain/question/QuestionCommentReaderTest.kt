package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
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

class QuestionCommentReaderTest {
    private val targetValidator = mockk<QuestionCommentTargetValidator>()
    private val commentRepository = mockk<QuestionCommentRepository>()
    private val reader = QuestionCommentReader(targetValidator, commentRepository)

    private val roomId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `첫 조회는 20개와 다음 커서를 반환한다`() {
        val baseTime = LocalDateTime.of(2026, 8, 13, 18, 0)
        val entities = (1L..21L).map { id -> comment(id, baseTime.plusSeconds(id)) }
        givenValidTarget()
        every { commentRepository.findPage(1L, null, null, any()) } returns entities

        val result = reader.getPage(roomId, targetMemberId, 1L, null)

        assertThat(result.comments.map { it.id }).containsExactlyElementsOf(1L..20L)
        assertThat(result.nextCursor).isEqualTo(
            QuestionCommentCursor(baseTime.plusSeconds(20), 20L),
        )
    }

    @Test
    fun `다음 조회는 커서 뒤의 댓글만 반환하고 마지막이면 커서가 없다`() {
        val cursor = QuestionCommentCursor(LocalDateTime.of(2026, 8, 13, 18, 20), 20L)
        givenValidTarget()
        every {
            commentRepository.findPage(1L, cursor.createdAt, cursor.id, any())
        } returns listOf(comment(21L, cursor.createdAt.plusSeconds(1)))

        val result = reader.getPage(roomId, targetMemberId, 1L, cursor)

        assertThat(result.comments.map { it.id }).containsExactly(21L)
        assertThat(result.nextCursor).isNull()
    }

    @Test
    fun `댓글 대상 검증에 실패하면 댓글을 조회하지 않는다`() {
        every {
            targetValidator.validate(roomId, targetMemberId, 1L)
        } throws CoreException(CoreErrorType.QUESTION_NOT_FOUND)

        assertThatThrownBy {
            reader.getPage(roomId, targetMemberId, 1L, null)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_NOT_FOUND)
        }

        verify(exactly = 0) { commentRepository.findPage(any(), any(), any(), any()) }
    }

    private fun givenValidTarget() {
        justRun { targetValidator.validate(roomId, targetMemberId, 1L) }
    }

    private fun comment(id: Long, createdAt: LocalDateTime): QuestionCommentEntity = mockk {
        every { this@mockk.id } returns id
        every { this@mockk.questionId } returns 1L
        every { this@mockk.authorMemberId } returns this@QuestionCommentReaderTest.authorMemberId
        every { this@mockk.commentType } returns QuestionCommentType.MEMO
        every { this@mockk.content } returns "댓글 $id"
        every { this@mockk.createdAt } returns createdAt
    }
}
