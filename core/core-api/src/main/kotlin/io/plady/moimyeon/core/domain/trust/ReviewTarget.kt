package io.plady.moimyeon.core.domain.trust

import java.util.UUID

data class ReviewTarget(
    val memberId: UUID,
    val status: ReviewTargetStatus,
)

enum class ReviewTargetStatus {
    WRITABLE,
    SUBMITTED,
}
