package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.enums.QuestionCommentType
import java.time.LocalDateTime

data class QuestionMemoRecord(
    val questionId: Long,
    val questionContent: String,
    val comments: List<QuestionMemoComment>,
)

data class QuestionMemoComment(
    val id: Long,
    val type: QuestionCommentType,
    val content: String,
    val createdAt: LocalDateTime,
)
