package io.plady.moimyeon.core.domain.closing

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ClosingService(
    private val submissionManager: ClosingSubmissionManager,
) {
    fun submit(
        memberId: UUID,
        roomId: UUID,
        evaluations: List<QuestionEvaluation>,
    ): ClosingSubmission {
        return submissionManager.submit(
            ClosingSubmissionCommand(
                roomId = roomId,
                memberId = memberId,
                evaluations = evaluations.toList(),
            ),
        )
    }
}
