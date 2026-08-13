package io.plady.moimyeon.core.domain.roundfeedback

import io.plady.moimyeon.core.enums.RoundFeedbackType
import java.util.UUID

data class RoundFeedbackCommand(
    val roomId: UUID,
    val intervieweeMemberId: UUID,
    val authorMemberId: UUID,
    val type: RoundFeedbackType,
    val content: String,
)
