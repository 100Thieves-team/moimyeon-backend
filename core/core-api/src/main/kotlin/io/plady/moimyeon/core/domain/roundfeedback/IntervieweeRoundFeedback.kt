package io.plady.moimyeon.core.domain.roundfeedback

import java.util.UUID

data class IntervieweeRoundFeedback(
    val selfFeedback: SelfFeedback?,
    val finalFeedbacks: List<FinalFeedbackCard>,
)

data class SelfFeedback(
    val id: Long,
    val content: String,
)

data class FinalFeedbackCard(
    val id: Long,
    val author: RoundFeedbackAuthor,
    val content: String?,
    val revealed: Boolean,
)

data class RoundFeedbackAuthor(
    val memberId: UUID,
    val displayName: String,
    val role: RoundFeedbackAuthorRole,
)
