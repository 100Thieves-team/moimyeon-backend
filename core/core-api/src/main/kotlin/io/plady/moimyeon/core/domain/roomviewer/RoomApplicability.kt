package io.plady.moimyeon.core.domain.roomviewer

import io.plady.moimyeon.core.enums.RoomStatus
import java.time.LocalDateTime

// 판정에 필요한 룸 쪽 사실만 추린 것. 목록의 RoomCard 와 상세의 RoomDetail 어느 쪽에서도 만들어진다 —
// 둘 중 하나에 의존하면 나머지 경로가 그 타입을 억지로 만들어야 한다.
data class RoomApplicability(
    val status: RoomStatus,
    val startAt: LocalDateTime,
    val currentParticipants: Int,
    val maxCapacity: Int,
) {
    // 차단 조건이 아니라 APPLY / APPLY_WAITLIST 분기다(§5 불변식 3).
    val full: Boolean get() = currentParticipants >= maxCapacity
}
