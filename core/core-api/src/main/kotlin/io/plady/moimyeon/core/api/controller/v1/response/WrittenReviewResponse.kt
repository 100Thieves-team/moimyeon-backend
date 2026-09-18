package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.trust.WrittenReview
import java.util.UUID

data class WrittenReviewResponse(
    val reviewId: Long,
    val roomId: UUID,
    val targetMemberId: UUID,
    val targetNickname: String,
    val tags: List<String>,
    val content: String,
    val anonymous: Boolean,
) {
    companion object {
        fun from(review: WrittenReview, nicknames: Map<UUID, String>): WrittenReviewResponse {
            return WrittenReviewResponse(
                reviewId = review.id,
                roomId = review.roomId,
                targetMemberId = review.targetMemberId,
                targetNickname = nicknames[review.targetMemberId] ?: WITHDRAWN_REVIEW_TARGET_NICKNAME,
                tags = review.tags.sorted(),
                content = review.content,
                anonymous = review.anonymous,
            )
        }
    }
}

private const val WITHDRAWN_REVIEW_TARGET_NICKNAME = "탈퇴한 회원"
