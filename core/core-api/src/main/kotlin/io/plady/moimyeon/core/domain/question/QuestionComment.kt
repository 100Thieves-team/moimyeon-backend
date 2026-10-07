package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.enums.QuestionCommentType
import java.time.LocalDateTime
import java.util.UUID

data class QuestionComment(
    val id: Long,
    val questionId: Long,
    val authorMemberId: UUID,
    val type: QuestionCommentType,
    val content: String,
    val createdAt: LocalDateTime,
)
