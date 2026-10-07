package io.plady.moimyeon.core.event.payload

import java.util.UUID

data class RoomHostDelegatedEventPayload(
    val roomId: UUID,
    val roomTitle: String,
    val previousHostMemberId: UUID,
    val newHostMemberId: UUID,
    val participantMemberIds: List<UUID>,
) : EventPayload
