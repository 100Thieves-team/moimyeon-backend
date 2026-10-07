package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.question.FollowUpQuestion
import io.plady.moimyeon.core.domain.question.QuestionCard
import io.plady.moimyeon.core.domain.question.QuestionCardSet
import io.plady.moimyeon.core.domain.question.QuestionResumeReference
import io.plady.moimyeon.core.domain.question.QuestionResumeSummary
import java.util.UUID

data class QuestionCardSetDetailResponse(
    val target: QuestionMemberResponse,
    val resumeSummary: QuestionResumeSummaryResponse,
    val questions: List<QuestionCardResponse>,
) {
    companion object {
        fun from(
            cardSet: QuestionCardSet,
            resumeReference: QuestionResumeReference,
            nicknames: Map<UUID, String>,
        ): QuestionCardSetDetailResponse {
            return QuestionCardSetDetailResponse(
                target = QuestionMemberResponse.of(cardSet.targetMemberId, nicknames),
                resumeSummary = QuestionResumeSummaryResponse.from(resumeReference.summary),
                questions = cardSet.questions.map { QuestionCardResponse.from(it, nicknames) },
            )
        }
    }
}

data class QuestionResumeSummaryResponse(
    val status: String,
    val text: String?,
) {
    companion object {
        fun from(summary: QuestionResumeSummary): QuestionResumeSummaryResponse {
            return when (summary) {
                is QuestionResumeSummary.Done -> QuestionResumeSummaryResponse(summary.status.name, summary.content)
                QuestionResumeSummary.Processing -> QuestionResumeSummaryResponse(summary.status.name, null)
                QuestionResumeSummary.Failed -> QuestionResumeSummaryResponse(summary.status.name, null)
            }
        }
    }
}

data class QuestionCardResponse(
    val questionId: Long,
    val author: QuestionMemberResponse,
    val content: String,
    val source: String,
    val asked: Boolean,
    val followUps: List<FollowUpQuestionResponse>,
) {
    companion object {
        fun from(question: QuestionCard, nicknames: Map<UUID, String>): QuestionCardResponse {
            return QuestionCardResponse(
                questionId = question.id,
                author = QuestionMemberResponse.of(question.authorMemberId, nicknames),
                content = question.content,
                source = question.source.name,
                asked = question.asked,
                followUps = question.followUps.map { FollowUpQuestionResponse.from(it, nicknames) },
            )
        }
    }
}

data class FollowUpQuestionResponse(
    val questionId: Long,
    val author: QuestionMemberResponse,
    val content: String,
    val source: String,
    val asked: Boolean,
) {
    companion object {
        fun from(question: FollowUpQuestion, nicknames: Map<UUID, String>): FollowUpQuestionResponse {
            return FollowUpQuestionResponse(
                questionId = question.id,
                author = QuestionMemberResponse.of(question.authorMemberId, nicknames),
                content = question.content,
                source = question.source.name,
                asked = question.asked,
            )
        }
    }
}
