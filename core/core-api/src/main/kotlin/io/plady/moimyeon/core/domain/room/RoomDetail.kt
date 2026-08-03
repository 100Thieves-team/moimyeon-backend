package io.plady.moimyeon.core.domain.room

import java.util.UUID

// 룸 단건 조회 결과 — 룸 애그리거트 + 파생 조회값(현재 인원·방장 식별자).
data class RoomDetail(
    val room: Room,
    val hostMemberId: UUID,
    val currentParticipants: Int,
)
