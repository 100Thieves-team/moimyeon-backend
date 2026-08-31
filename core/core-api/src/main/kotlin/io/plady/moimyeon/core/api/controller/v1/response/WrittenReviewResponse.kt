package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.trust.WrittenReview
import java.util.UUID

data class WrittenReviewResponse(
    val reviewId: Long,
    val roomId: UUID,
    val targetMemberId: UUID,
    val tags: List<String>,
    val content: String?,
    val anonymous: Boolean,
) {
    companion object {
        fun from(review: WrittenReview): WrittenReviewResponse {
            return WrittenReviewResponse(
                reviewId = review.id,
                roomId = review.roomId,
                targetMemberId = review.targetMemberId,
                tags = review.tags.sorted(),
                content = review.content,
                anonymous = review.anonymous,
            )
        }
    }
}
