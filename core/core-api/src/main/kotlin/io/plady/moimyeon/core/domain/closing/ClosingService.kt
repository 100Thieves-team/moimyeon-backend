package io.plady.moimyeon.core.domain.closing

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class ClosingService(
    private val submissionManager: ClosingSubmissionManager,
    private val accessValidator: ClosingAccessValidator,
    private val questionReader: ClosingQuestionReader,
) {
    fun getQuestions(memberId: UUID, roomId: UUID): List<ClosingQuestion> {
        accessValidator.validateParticipant(roomId, memberId)
        return questionReader.getQuestions(roomId, memberId)
    }

    fun submit(
        memberId: UUID,
        roomId: UUID,
        evaluations: List<QuestionEvaluation>,
    ): ClosingSubmission {
        log.debug { "closing.submit memberId=$memberId roomId=$roomId evaluations=${evaluations.size}" }
        return submissionManager.submit(
            ClosingSubmissionCommand(
                roomId = roomId,
                memberId = memberId,
                evaluations = evaluations.toList(),
            ),
        )
    }
}
