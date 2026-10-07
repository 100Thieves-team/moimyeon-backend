package io.plady.moimyeon.core.domain.closing

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.storage.db.core.ClosingQuestionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class ClosingQuestionReader(
    private val closingQuestionRepository: ClosingQuestionRepository,
) {
    @Transactional(readOnly = true)
    fun getQuestions(roomId: UUID, memberId: UUID): List<ClosingQuestion> {
        log.debug { "closing-question.reader.getQuestions roomId=$roomId memberId=$memberId" }
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
