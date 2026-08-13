package io.plady.moimyeon.core.domain.question

import java.time.LocalDateTime

data class QuestionCommentCursor(
    val createdAt: LocalDateTime,
    val id: Long,
)
