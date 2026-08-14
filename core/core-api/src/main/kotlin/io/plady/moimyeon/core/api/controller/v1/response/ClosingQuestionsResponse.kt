package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.closing.ClosingQuestion
import java.util.UUID

data class ClosingQuestionsResponse(
    val questions: List<ClosingQuestionResponse>,
) {
    companion object {
        fun from(questions: List<ClosingQuestion>): ClosingQuestionsResponse {
            return ClosingQuestionsResponse(questions.map(ClosingQuestionResponse::from))
        }
    }
}

data class ClosingQuestionResponse(
    val questionId: Long,
    val authorMemberId: UUID,
    val content: String,
    val source: String,
) {
    companion object {
        fun from(question: ClosingQuestion): ClosingQuestionResponse = ClosingQuestionResponse(
            questionId = question.id,
            authorMemberId = question.authorMemberId,
            content = question.content,
            source = question.source.name,
        )
    }
}
