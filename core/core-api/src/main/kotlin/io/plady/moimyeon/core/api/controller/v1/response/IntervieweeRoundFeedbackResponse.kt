package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.roundfeedback.IntervieweeRoundFeedback
import java.util.UUID

data class IntervieweeRoundFeedbackResponse(
    val selfFeedback: SelfFeedbackResponse?,
    val finalFeedbacks: List<FinalFeedbackCardResponse>,
) {
    companion object {
        fun from(feedback: IntervieweeRoundFeedback): IntervieweeRoundFeedbackResponse {
            return IntervieweeRoundFeedbackResponse(
                selfFeedback = feedback.selfFeedback?.let { SelfFeedbackResponse(it.id, it.content) },
                finalFeedbacks = feedback.finalFeedbacks.map { card ->
                    FinalFeedbackCardResponse(
                        feedbackId = card.id,
                        author = RoundFeedbackAuthorResponse(
                            memberId = card.author.memberId,
                            displayName = card.author.displayName,
                            role = card.author.role.name,
                        ),
                        content = card.content,
                        revealed = card.revealed,
                    )
                },
            )
        }
    }
}

data class SelfFeedbackResponse(
    val feedbackId: Long,
    val content: String,
)

data class FinalFeedbackCardResponse(
    val feedbackId: Long,
    val author: RoundFeedbackAuthorResponse,
    val content: String?,
    val revealed: Boolean,
)

data class RoundFeedbackAuthorResponse(
    val memberId: UUID,
    val displayName: String,
    val role: String,
)
