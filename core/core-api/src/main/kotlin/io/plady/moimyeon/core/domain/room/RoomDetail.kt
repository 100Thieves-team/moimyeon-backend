package io.plady.moimyeon.core.domain.room

import java.util.UUID

// 룸 단건 조회 결과 — 룸 애그리거트 + 파생 조회값(현재 인원·방장 식별자·대기 신청 수).
data class RoomDetail(
    val room: Room,
    val hostMemberId: UUID,
    val currentParticipants: Int,
    val pendingApplicationCount: Int = 0,
) {
    // 모집중/마감은 저장값이 아니라 정원 충족 여부로 계산한다(핵심 결정). RoomCard 와 같은 파생이다.
    val recruitStatus: RecruitStatus get() = RecruitStatus.of(currentParticipants, room.capacity)
}
