package io.plady.moimyeon.core.event.payload

import java.util.UUID

data class RoomCommentPostedEventPayload(
    val commentId: Long,
    val roomId: UUID,
    val roomTitle: String,
    val authorMemberId: UUID,
    val participantMemberIds: List<UUID>,
) : EventPayload
