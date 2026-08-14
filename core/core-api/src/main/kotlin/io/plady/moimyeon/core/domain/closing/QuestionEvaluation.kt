package io.plady.moimyeon.core.domain.closing

import io.plady.moimyeon.core.enums.QuestionVote

data class QuestionEvaluation(
    val questionId: Long,
    val vote: QuestionVote,
)
