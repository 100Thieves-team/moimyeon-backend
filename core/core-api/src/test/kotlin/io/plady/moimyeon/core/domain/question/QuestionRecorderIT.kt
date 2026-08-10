package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.QuestionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class QuestionRecorderIT(
    private val questionRecorder: QuestionRecorder,
    private val questionRepository: QuestionRepository,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `질문을 삭제한 뒤 같은 대상에게 재작성하면 기존 행은 숨기고 새 질문을 남긴다`() {
        val originalQuestionId = questionRecorder.record(
            roomId,
            targetMemberId,
            authorMemberId,
            null,
            "기존 질문",
            QuestionSource.PREPARATION,
        )
        questionRecorder.removeOwnedBy(
            roomId,
            originalQuestionId,
            authorMemberId,
            LocalDateTime.of(2026, 8, 10, 3, 0),
        )

        val rewrittenQuestionId = questionRecorder.record(
            roomId,
            targetMemberId,
            authorMemberId,
            null,
            "재작성한 질문",
            QuestionSource.PREPARATION,
        )
        questionRepository.flush()

        assertThat(rewrittenQuestionId).isNotEqualTo(originalQuestionId)
        assertThat(questionRepository.findByIdAndDeletedAtIsNull(originalQuestionId)).isNull()
        assertThat(questionRepository.findByIdAndDeletedAtIsNull(rewrittenQuestionId)?.content)
            .isEqualTo("재작성한 질문")
        assertThat(
            questionRepository.findByRoomIdAndTargetMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                targetMemberId,
            ).map { it.id },
        ).containsExactly(rewrittenQuestionId)
    }

    @Test
    fun `본인이 작성한 꼬리질문만 달린 원 질문을 삭제하면 모두 활성 조회에서 제외한다`() {
        val originalQuestionId = questionRecorder.record(
            roomId,
            targetMemberId,
            authorMemberId,
            null,
            "원 질문",
            QuestionSource.PREPARATION,
        )
        val followUpQuestionId = questionRecorder.record(
            roomId,
            targetMemberId,
            authorMemberId,
            originalQuestionId,
            "본인이 작성한 꼬리질문",
            QuestionSource.PREPARATION,
        )

        questionRecorder.removeOwnedBy(
            roomId,
            originalQuestionId,
            authorMemberId,
            LocalDateTime.of(2026, 8, 10, 3, 0),
        )
        questionRepository.flush()

        assertThat(questionRepository.findByIdAndDeletedAtIsNull(originalQuestionId)).isNull()
        assertThat(questionRepository.findByIdAndDeletedAtIsNull(followUpQuestionId)).isNull()
    }

    @Test
    fun `다른 작성자의 활성 꼬리질문이 달린 원 질문은 삭제할 수 없다`() {
        val originalQuestionId = questionRecorder.record(
            roomId,
            targetMemberId,
            authorMemberId,
            null,
            "원 질문",
            QuestionSource.PREPARATION,
        )
        val ownFollowUpQuestionId = questionRecorder.record(
            roomId,
            targetMemberId,
            authorMemberId,
            originalQuestionId,
            "본인이 작성한 꼬리질문",
            QuestionSource.PREPARATION,
        )
        val otherAuthorFollowUpQuestionId = questionRecorder.record(
            roomId,
            targetMemberId,
            UUID.randomUUID(),
            originalQuestionId,
            "다른 작성자의 꼬리질문",
            QuestionSource.PREPARATION,
        )

        assertThatThrownBy {
            questionRecorder.removeOwnedBy(
                roomId,
                originalQuestionId,
                authorMemberId,
                LocalDateTime.of(2026, 8, 10, 3, 0),
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType)
                .isEqualTo(CoreErrorType.QUESTION_HAS_OTHER_FOLLOW_UP)
        }

        assertThat(questionRepository.findByIdAndDeletedAtIsNull(originalQuestionId)).isNotNull()
        assertThat(questionRepository.findByIdAndDeletedAtIsNull(ownFollowUpQuestionId)).isNotNull()
        assertThat(questionRepository.findByIdAndDeletedAtIsNull(otherAuthorFollowUpQuestionId)).isNotNull()
    }
}
