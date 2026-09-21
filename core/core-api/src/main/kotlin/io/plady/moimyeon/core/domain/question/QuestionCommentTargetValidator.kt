package io.plady.moimyeon.core.domain.question

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.QuestionRepository
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class QuestionCommentTargetValidator(
    private val questionRepository: QuestionRepository,
) {
    fun validate(roomId: UUID, targetMemberId: UUID, questionId: Long) {
        log.debug { "question-comment-target.validator.validate roomId=$roomId targetMemberId=$targetMemberId questionId=$questionId" }
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
