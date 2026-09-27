package io.plady.moimyeon.core.event.payload

import java.util.UUID

data class RoomCanceledEventPayload(
    val roomId: UUID,
    val roomTitle: String,
    val canceledByMemberId: UUID,
    val participantMemberIds: List<UUID>,
    val closedApplicantMemberIds: List<UUID>,
) : EventPayload
