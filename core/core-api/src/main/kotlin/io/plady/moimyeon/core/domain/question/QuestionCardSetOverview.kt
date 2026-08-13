package io.plady.moimyeon.core.domain.question

data class QuestionCardSetOverview(
    val cardSets: List<QuestionCardSet>,
    val myCardSetPreparerCount: Int,
)
