package io.plady.moimyeon.core.domain.trust

import java.util.UUID

data class ReviewTarget(
    val memberId: UUID,
    val status: ReviewTargetStatus,
    val reviewId: Long? = null,
)

enum class ReviewTargetStatus {
    WRITABLE,
    SUBMITTED,
}
