package io.plady.moimyeon.core.domain.roundfeedback

import io.plady.moimyeon.core.enums.QuestionCommentType
import java.time.LocalDateTime

data class RoundQuestionRecord(
    val questionId: Long,
    val questionContent: String,
    val comments: List<RoundQuestionComment>,
)

data class RoundQuestionComment(
    val id: Long,
    val type: QuestionCommentType,
    val content: String,
    val createdAt: LocalDateTime,
)
