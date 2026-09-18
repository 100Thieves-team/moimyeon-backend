package io.plady.moimyeon.core.api.controller.v1.response

data class ReviewSubmittedResponse(
    val reviewId: Long,
) {
    companion object {
        fun of(reviewId: Long): ReviewSubmittedResponse = ReviewSubmittedResponse(reviewId)
    }
}
