package io.plady.moimyeon.core.event.payload

import java.util.UUID

data class ReviewPublishedEventPayload(
    val reviewId: Long,
    val roomId: UUID,
    val roomTitle: String,
    val authorMemberId: UUID,
    val targetMemberId: UUID,
) : EventPayload
