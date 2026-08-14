package io.plady.moimyeon.core.domain.question

data class QuestionCommentPage(
    val comments: List<QuestionComment>,
    val nextCursor: QuestionCommentCursor?,
)
