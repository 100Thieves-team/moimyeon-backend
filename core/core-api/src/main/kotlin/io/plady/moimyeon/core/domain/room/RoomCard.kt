package io.plady.moimyeon.core.domain.room

data class RoomCard(
    val room: Room,
    val currentParticipants: Int,
    val pendingApplications: Int,
) {
    val recruitStatus: RecruitStatus get() = RecruitStatus.of(currentParticipants, room.capacity)
}

// nextCursor 가 null 이면 마지막 페이지다.
data class RoomCardPage(
    val cards: List<RoomCard>,
    val nextCursor: RoomCursor?,
    val totalCount: Long,
) {
    companion object {
        val EMPTY = RoomCardPage(cards = emptyList(), nextCursor = null, totalCount = 0)
    }
}
