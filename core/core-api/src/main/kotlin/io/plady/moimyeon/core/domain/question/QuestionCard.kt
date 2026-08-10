package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.enums.QuestionSource
import java.util.UUID

data class QuestionCard(
    val id: Long,
    val authorMemberId: UUID,
    val content: String,
    val source: QuestionSource,
    val followUps: List<FollowUpQuestion>,
)
