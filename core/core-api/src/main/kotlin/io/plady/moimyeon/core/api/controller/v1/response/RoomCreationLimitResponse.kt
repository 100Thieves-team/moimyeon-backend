package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.room.RoomCreationLimit

data class RoomCreationLimitResponse(
    val activeRoomCount: Long,
    val limit: Int,
    val remaining: Int,
) {
    companion object {
        fun from(creationLimit: RoomCreationLimit): RoomCreationLimitResponse = RoomCreationLimitResponse(
            activeRoomCount = creationLimit.activeRoomCount,
            limit = creationLimit.limit,
            remaining = creationLimit.remaining,
        )
    }
}
