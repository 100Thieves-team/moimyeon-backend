package io.plady.moimyeon.core.api.controller.v1.response

import java.util.UUID

data class ReviewTargetsResponse(
    val submittedCount: Int,
    val totalCount: Int,
    val targets: List<LegacyReviewTargetResponse>,
) {
    companion object {
        fun from(overview: ReviewOverviewResponse): ReviewTargetsResponse {
            val reviewIdByTargetId = overview.reviews.associate { it.targetMemberId to it.reviewId }

            return ReviewTargetsResponse(
                submittedCount = overview.submittedCount,
                totalCount = overview.totalCount,
                targets = overview.targets.map { target ->
                    LegacyReviewTargetResponse(
                        memberId = target.memberId,
                        nickname = target.nickname,
                        status = target.status,
                        reviewId = reviewIdByTargetId[target.memberId],
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
