package io.plady.moimyeon.core.event.payload

import java.util.UUID

data class RoomApplicationSubmittedEventPayload(
    val applicationId: Long,
    val roomId: UUID,
    val roomTitle: String,
    val hostMemberId: UUID,
    val applicantMemberId: UUID,
) : EventPayload
