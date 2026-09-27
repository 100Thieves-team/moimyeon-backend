package io.plady.moimyeon.core.event.payload

import java.util.UUID

data class RoomConfirmedEventPayload(
    val roomId: UUID,
    val roomTitle: String,
    val hostMemberId: UUID,
    val participantMemberIds: List<UUID>,
    val closedApplicantMemberIds: List<UUID>,
) : EventPayload
