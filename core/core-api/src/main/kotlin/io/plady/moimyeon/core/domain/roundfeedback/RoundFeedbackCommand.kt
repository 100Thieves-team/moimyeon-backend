package io.plady.moimyeon.core.domain.roundfeedback

import java.util.UUID

data class RoundFeedbackCommand(
    val roomId: UUID,
    val intervieweeMemberId: UUID,
    val authorMemberId: UUID,
    val content: String,
)
