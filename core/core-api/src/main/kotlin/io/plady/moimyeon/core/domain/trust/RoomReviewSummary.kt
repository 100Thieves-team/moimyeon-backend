package io.plady.moimyeon.core.domain.trust

import java.util.UUID

data class RoomReviewSummary(
    val roomId: UUID,
    val attendedParticipantCount: Int,
    val status: RoomReviewStatus,
)

enum class RoomReviewStatus {
    WRITABLE,
    WRITTEN,
    NOT_ELIGIBLE_ABSENT,
    NOT_ELIGIBLE_NO_TARGET,
}
