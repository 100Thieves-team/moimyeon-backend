package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.QuestionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class QuestionUsageMarker(
    private val questionRepository: QuestionRepository,
) {
    @Transactional
    fun changeAsked(roomId: UUID, targetMemberId: UUID, questionId: Long, asked: Boolean) {
        val question = requireFound(
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, questionId),
            CoreErrorType.QUESTION_NOT_FOUND,
        )
        requireBusiness(
            question.targetMemberId == targetMemberId,
            CoreErrorType.QUESTION_NOT_FOUND,
        )
        question.changeAsked(asked)
    }
}
