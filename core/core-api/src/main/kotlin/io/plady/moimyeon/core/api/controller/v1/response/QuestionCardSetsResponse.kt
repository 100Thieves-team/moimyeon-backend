package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.question.QuestionCardSet
import java.util.UUID

data class QuestionCardSetsResponse(
    val myCardSetPreparerCount: Int,
    val cardSets: List<QuestionCardSetSummaryResponse>,
) {
    companion object {
        fun from(
            myCardSetPreparerCount: Int,
            cardSets: List<QuestionCardSet>,
            nicknames: Map<UUID, String>,
        ): QuestionCardSetsResponse {
            return QuestionCardSetsResponse(
                myCardSetPreparerCount = myCardSetPreparerCount,
                cardSets = cardSets.map { cardSet ->
                    QuestionCardSetSummaryResponse(
                        target = QuestionMemberResponse.of(cardSet.targetMemberId, nicknames),
                        questionCount = cardSet.questions.size,
                        followUpQuestionCount = cardSet.questions.sumOf { it.followUps.size },
                    )
                },
            )
        }
    }
}

data class QuestionCardSetSummaryResponse(
    val target: QuestionMemberResponse,
    val questionCount: Int,
    val followUpQuestionCount: Int,
)
