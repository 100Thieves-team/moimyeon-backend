package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.QuestionRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class QuestionCommentTargetValidator(
    private val questionRepository: QuestionRepository,
) {
    fun validate(roomId: UUID, targetMemberId: UUID, questionId: Long) {
        val question = requireFound(
            questionRepository.findByIdAndDeletedAtIsNull(questionId),
            CoreErrorType.QUESTION_NOT_FOUND,
        )
        requireBusiness(
            question.roomId == roomId &&
                question.targetMemberId == targetMemberId &&
                question.parentQuestionId == null,
            CoreErrorType.QUESTION_NOT_FOUND,
        )
    }
}
