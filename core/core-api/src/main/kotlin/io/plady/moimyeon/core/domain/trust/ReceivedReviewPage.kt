package io.plady.moimyeon.core.domain.trust

data class ReceivedReviewPage(
    val reviews: List<ReceivedReview>,
    val totalCount: Long,
    val hasNext: Boolean,
)
