package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.closing.QuestionEvaluation
import io.plady.moimyeon.core.enums.QuestionVote
import java.util.UUID

data class SubmitClosingRequest(
    val roomId: UUID,
    val evaluations: List<QuestionEvaluationRequest>,
) {
    fun toEvaluations(): List<QuestionEvaluation> = evaluations.map(QuestionEvaluationRequest::toEvaluation)
}

data class QuestionEvaluationRequest(
    val questionId: Long,
    val vote: QuestionVote,
) {
    fun toEvaluation(): QuestionEvaluation = QuestionEvaluation(questionId, vote)
}
