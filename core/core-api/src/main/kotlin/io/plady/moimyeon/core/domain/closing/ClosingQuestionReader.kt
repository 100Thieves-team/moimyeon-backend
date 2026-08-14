package io.plady.moimyeon.core.domain.closing

import io.plady.moimyeon.storage.db.core.ClosingQuestionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ClosingQuestionReader(
    private val closingQuestionRepository: ClosingQuestionRepository,
) {
    @Transactional(readOnly = true)
    fun getQuestions(roomId: UUID, memberId: UUID): List<ClosingQuestion> {
        return closingQuestionRepository.findAllAskedTopLevelByRoomIdAndTargetMemberId(roomId, memberId)
            .map { question ->
                ClosingQuestion(
                    id = question.id,
                    authorMemberId = question.authorMemberId,
                    content = question.content,
                    source = question.source,
                )
            }
    }
}
