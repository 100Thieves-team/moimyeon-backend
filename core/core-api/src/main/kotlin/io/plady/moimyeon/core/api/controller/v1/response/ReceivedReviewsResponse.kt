package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.trust.ReceivedReview
import io.plady.moimyeon.core.domain.trust.ReceivedReviewPage

data class ReceivedReviewsResponse(
    val totalCount: Long,
    val reviews: List<ReceivedReviewResponse>,
    val hasNext: Boolean,
) {
    companion object {
        fun from(page: ReceivedReviewPage): ReceivedReviewsResponse {
            return ReceivedReviewsResponse(
                totalCount = page.totalCount,
                reviews = page.reviews.map(ReceivedReviewResponse::from),
                hasNext = page.hasNext,
            )
        }
    }
}

data class ReceivedReviewResponse(
    val reviewId: Long,
    val tags: List<String>,
    val content: String?,
) {
    companion object {
        fun from(review: ReceivedReview): ReceivedReviewResponse {
            return ReceivedReviewResponse(
                reviewId = review.id,
                tags = review.tags.sorted(),
                content = review.content,
            )
        }
    }
}
