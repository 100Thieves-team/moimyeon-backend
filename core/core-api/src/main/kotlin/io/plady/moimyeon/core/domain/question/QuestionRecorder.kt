package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class QuestionRecorder(
    private val questionRepository: QuestionRepository,
) {
    @Transactional
    fun record(
        roomId: UUID,
        targetMemberId: UUID,
        authorMemberId: UUID,
        parentQuestionId: Long?,
        content: String,
        source: QuestionSource,
    ): Long {
        if (parentQuestionId != null) {
            validateParent(roomId, targetMemberId, parentQuestionId)
        }
        return questionRepository.save(
            QuestionEntity(
                roomId = roomId,
                targetMemberId = targetMemberId,
                authorMemberId = authorMemberId,
                parentQuestionId = parentQuestionId,
                content = content,
                source = source,
            ),
        ).id
    }

    @Transactional
    fun removeOwnedBy(
        roomId: UUID,
        questionId: Long,
        authorMemberId: UUID,
        deletedAt: LocalDateTime,
    ) {
        val question = requireFound(
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, questionId),
            CoreErrorType.QUESTION_NOT_FOUND,
        )
        requireBusiness(
            question.authorMemberId == authorMemberId,
            CoreErrorType.QUESTION_PREPARATION_FORBIDDEN,
        )
        if (question.parentQuestionId == null) {
            requireBusiness(
                !questionRepository.existsByParentQuestionIdAndAuthorMemberIdNotAndDeletedAtIsNull(
                    questionId,
                    authorMemberId,
                ),
                CoreErrorType.QUESTION_HAS_OTHER_FOLLOW_UP,
            )
            questionRepository.findAllByParentQuestionIdAndAuthorMemberIdAndDeletedAtIsNull(
                questionId,
                authorMemberId,
            ).forEach { it.delete(deletedAt) }
        }
        question.delete(deletedAt)
    }

    private fun validateParent(roomId: UUID, targetMemberId: UUID, parentQuestionId: Long) {
        val parent = requireFound(
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, parentQuestionId),
            CoreErrorType.QUESTION_NOT_FOUND,
        )
        requireBusiness(
            parent.roomId == roomId &&
                parent.targetMemberId == targetMemberId &&
                parent.parentQuestionId == null,
            CoreErrorType.QUESTION_NOT_FOUND,
        )
    }
}
