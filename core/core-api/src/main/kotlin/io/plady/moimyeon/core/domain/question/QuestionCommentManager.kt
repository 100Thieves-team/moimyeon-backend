package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.QuestionCommentEntity
import io.plady.moimyeon.storage.db.core.QuestionCommentRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class QuestionCommentManager(
    private val targetValidator: QuestionCommentTargetValidator,
    private val questionCommentRepository: QuestionCommentRepository,
) {
    @Transactional
    fun record(
        roomId: UUID,
        targetMemberId: UUID,
        questionId: Long,
        authorMemberId: UUID,
        type: QuestionCommentType,
        content: String,
    ): Long {
        targetValidator.validate(roomId, targetMemberId, questionId)
        return questionCommentRepository.save(
            QuestionCommentEntity(
                questionId = questionId,
                authorMemberId = authorMemberId,
                commentType = type,
                content = content,
            ),
        ).id
    }

    @Transactional
    fun toggleType(
        roomId: UUID,
        targetMemberId: UUID,
        questionId: Long,
        commentId: Long,
        authorMemberId: UUID,
        type: QuestionCommentType,
    ) {
        targetValidator.validate(roomId, targetMemberId, questionId)
        getOwnedComment(questionId, commentId, authorMemberId).toggleType(type)
    }

    @Transactional
    fun edit(
        roomId: UUID,
        targetMemberId: UUID,
        questionId: Long,
        commentId: Long,
        authorMemberId: UUID,
        content: String,
    ) {
        targetValidator.validate(roomId, targetMemberId, questionId)
        getOwnedComment(questionId, commentId, authorMemberId).edit(content)
    }

    @Transactional
    fun remove(
        roomId: UUID,
        targetMemberId: UUID,
        questionId: Long,
        commentId: Long,
        authorMemberId: UUID,
        deletedAt: LocalDateTime,
    ) {
        targetValidator.validate(roomId, targetMemberId, questionId)
        getOwnedComment(questionId, commentId, authorMemberId).delete(deletedAt)
    }

    private fun getOwnedComment(
        questionId: Long,
        commentId: Long,
        authorMemberId: UUID,
    ): QuestionCommentEntity {
        val comment = requireFound(
            questionCommentRepository.findForUpdateByIdAndDeletedAtIsNull(commentId),
            CoreErrorType.QUESTION_COMMENT_NOT_FOUND,
        )
        requireBusiness(
            comment.questionId == questionId && comment.authorMemberId == authorMemberId,
            CoreErrorType.QUESTION_COMMENT_NOT_FOUND,
        )
        return comment
    }
}
