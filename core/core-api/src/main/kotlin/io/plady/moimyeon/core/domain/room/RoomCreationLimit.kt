package io.plady.moimyeon.core.domain.room

// 생성 전 경고에 필요한 것(「룸 생성」 §4.7). 개수만 내려주면 화면이 한도 3 을 스스로 알아야 하고,
// 그러면 정책이 바뀔 때 서버와 화면이 갈린다.
data class RoomCreationLimit(
    val activeRoomCount: Long,
    val limit: Int,
    val remaining: Int,
) {
    companion object {
        fun of(activeRoomCount: Long): RoomCreationLimit = RoomCreationLimit(
            activeRoomCount = activeRoomCount,
            limit = ActiveRoomLimit.MAX,
            remaining = ActiveRoomLimit.remaining(activeRoomCount),
        )
    }
}
