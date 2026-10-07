package io.plady.moimyeon.core.domain.room

import java.util.UUID

data class RoomDetail(
    val room: Room,
    val hostMemberId: UUID,
    val currentParticipants: Int,
    val pendingApplicationCount: Int = 0,
    val previouslyConfirmed: Boolean = false,
) {
    val recruitStatus: RecruitStatus get() = RecruitStatus.of(currentParticipants, room.capacity)
}
