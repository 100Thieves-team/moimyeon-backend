package io.plady.moimyeon.core.event.payload

import java.util.UUID

data class RoomApplicationRejectedEventPayload(
    val applicationId: Long,
    val roomId: UUID,
    val roomTitle: String,
    val applicantMemberId: UUID,
) : EventPayload
