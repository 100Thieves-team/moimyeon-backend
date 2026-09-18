package io.plady.moimyeon.core.domain.closing

import io.plady.moimyeon.core.enums.QuestionSource
import java.util.UUID

data class ClosingQuestion(
    val id: Long,
    val authorMemberId: UUID,
    val content: String,
    val source: QuestionSource,
)
