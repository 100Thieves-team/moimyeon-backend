package io.plady.moimyeon.core.domain.question

import java.util.UUID

data class QuestionCardSet(
    val targetMemberId: UUID,
    val questions: List<QuestionCard>,
)
