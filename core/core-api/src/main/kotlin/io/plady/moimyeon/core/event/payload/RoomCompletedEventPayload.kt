package io.plady.moimyeon.core.event.payload

import java.util.UUID

// completedByMemberId 가 null 이면 자동 완료다.
data class RoomCompletedEventPayload(
    val roomId: UUID,
    val roomTitle: String,
    val completedByMemberId: UUID?,
    val confirmedParticipantMemberIds: List<UUID>,
    val attendedMemberIds: List<UUID>,
) : EventPayload
