package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.RoomApplicationStatus

// 수락·반려 처리 결과. 모집 상태(모집 중/마감)는 저장값이 아니라 정원 충족 여부로 계산하므로
// 여기서는 현재 인원과 최대 인원만 담고, 라벨링·CLOSED 판정은 API 응답 변환이 맡는다.
data class ApplicationDecision(
    val applicationId: Long,
    val status: RoomApplicationStatus,
    val currentParticipants: Int,
    val maxCapacity: Int,
)
