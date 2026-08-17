package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.trust.ReviewTarget
import io.plady.moimyeon.core.domain.trust.ReviewTargetStatus
import java.util.UUID

data class ReviewTargetsResponse(
    val submittedCount: Int,
    val totalCount: Int,
    val targets: List<ReviewTargetResponse>,
) {
    companion object {
        fun from(targets: List<ReviewTarget>, nicknames: Map<UUID, String>): ReviewTargetsResponse {
            return ReviewTargetsResponse(
                submittedCount = targets.count { it.status == ReviewTargetStatus.SUBMITTED },
                totalCount = targets.size,
                targets = targets.map { target ->
                    ReviewTargetResponse(
                        memberId = target.memberId,
                        nickname = nicknames[target.memberId] ?: WITHDRAWN_REVIEW_TARGET_NICKNAME,
                        status = target.status,
                        reviewId = target.reviewId,
                    )
                },
            )
        }
    }
}

data class ReviewTargetResponse(
    val memberId: UUID,
    val nickname: String,
    val status: ReviewTargetStatus,
    val reviewId: Long?,
)

private const val WITHDRAWN_REVIEW_TARGET_NICKNAME = "탈퇴한 회원"
