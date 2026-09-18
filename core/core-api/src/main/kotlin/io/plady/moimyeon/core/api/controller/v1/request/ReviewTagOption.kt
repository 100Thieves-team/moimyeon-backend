package io.plady.moimyeon.core.api.controller.v1.request

enum class ReviewTagOption(
    val label: String,
) {
    PUNCTUAL("시간을 잘 지켜요"),
    WELL_PREPARED("준비가 성실해요"),
    INSIGHTFUL_QUESTIONS("질문이 날카로워요"),
    SPECIFIC_FEEDBACK("피드백이 구체적이에요"),
    GOOD_COMMUNICATION("소통이 원활해요"),
    ;

    companion object {
        val labels: Set<String> = entries.mapTo(linkedSetOf(), ReviewTagOption::label)
    }
}
