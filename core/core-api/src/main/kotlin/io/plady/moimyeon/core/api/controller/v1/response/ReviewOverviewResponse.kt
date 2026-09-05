package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.trust.ReviewTarget
import io.plady.moimyeon.core.domain.trust.WrittenReview
import java.util.UUID

data class ReviewOverviewResponse(
    val submittedCount: Int,
    val totalCount: Int,
    val targets: List<ReviewTargetResponse>,
    val reviews: List<ReviewOverviewWrittenReviewResponse>,
) {
    companion object {
        fun from(
            targets: List<ReviewTarget>,
            writtenReviews: List<WrittenReview>,
            nicknames: Map<UUID, String>,
        ): ReviewOverviewResponse {
            val writtenReviewsByTargetId = writtenReviews.associateBy(WrittenReview::targetMemberId)

            return ReviewOverviewResponse(
                submittedCount = targets.count { it.memberId in writtenReviewsByTargetId },
                totalCount = targets.size,
                targets = targets.map { target ->
                    ReviewTargetResponse(
                        memberId = target.memberId,
                        nickname = nicknames[target.memberId] ?: WITHDRAWN_REVIEW_TARGET_NICKNAME,
                        status = if (target.memberId in writtenReviewsByTargetId) {
                            ReviewTargetStatus.SUBMITTED
                        } else {
                            ReviewTargetStatus.WRITABLE
                        },
                    )
                },
                reviews = writtenReviews.map(ReviewOverviewWrittenReviewResponse::from),
            )
        }
    }
}

data class ReviewTargetResponse(
    val memberId: UUID,
    val nickname: String,
    val status: ReviewTargetStatus,
)

data class ReviewOverviewWrittenReviewResponse(
    val reviewId: Long,
    val targetMemberId: UUID,
    val tags: List<String>,
    val content: String,
    val anonymous: Boolean,
) {
    companion object {
        fun from(review: WrittenReview): ReviewOverviewWrittenReviewResponse {
            return ReviewOverviewWrittenReviewResponse(
                reviewId = review.id,
                targetMemberId = review.targetMemberId,
                tags = review.tags.sorted(),
                content = review.content,
                anonymous = review.anonymous,
            )
        }
    }
}

enum class ReviewTargetStatus {
    WRITABLE,
    SUBMITTED,
}

private const val WITHDRAWN_REVIEW_TARGET_NICKNAME = "탈퇴한 회원"
