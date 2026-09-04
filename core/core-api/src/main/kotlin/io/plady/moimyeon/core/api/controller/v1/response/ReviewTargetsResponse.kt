package io.plady.moimyeon.core.api.controller.v1.response

import java.util.UUID

data class ReviewTargetsResponse(
    val submittedCount: Int,
    val totalCount: Int,
    val targets: List<LegacyReviewTargetResponse>,
) {
    companion object {
        fun from(overview: ReviewOverviewResponse): ReviewTargetsResponse {
            val writtenReviewsByTargetId = overview.reviews.associateBy { it.targetMemberId }

            return ReviewTargetsResponse(
                submittedCount = overview.submittedCount,
                totalCount = overview.totalCount,
                targets = overview.targets.map { target ->
                    val writtenReview = writtenReviewsByTargetId[target.memberId]
                    LegacyReviewTargetResponse(
                        memberId = target.memberId,
                        nickname = target.nickname,
                        status = if (writtenReview == null) {
                            ReviewTargetStatus.WRITABLE
                        } else {
                            ReviewTargetStatus.SUBMITTED
                        },
                        reviewId = writtenReview?.reviewId,
                    )
                },
            )
        }
    }
}

data class LegacyReviewTargetResponse(
    val memberId: UUID,
    val nickname: String,
    val status: ReviewTargetStatus,
    val reviewId: Long?,
)
