package io.plady.moimyeon.core.domain.room

// 탐색 목록의 한 줄 — 룸 애그리거트 + 파생 조회값(현재 인원·대기 신청 수).
// 단건 조회의 RoomDetail 과 같은 모양이다. 표시명(회사·공고·직무·지역)은 담지 않는다 —
// 다른 개념의 조회라 조립은 Facade 의 일이다.
data class RoomCard(
    val room: Room,
    val currentParticipants: Int,
    val pendingApplications: Int,
) {
    // 모집 상태는 저장값이 아니라 정원 충족 여부로 계산한다(핵심 결정 4).
    val recruitStatus: RecruitStatus get() = RecruitStatus.of(currentParticipants, room.capacity)
}

// 커서 페이지 한 장. nextCursor 가 null 이면 마지막 페이지다.
// 문자열 토큰이 아니라 커서 좌표를 담는다 — 인코딩은 web 레이어가 한다.
data class RoomCardPage(
    val cards: List<RoomCard>,
    val nextCursor: RoomCursor?,
    val totalCount: Long,
) {
    companion object {
        val EMPTY = RoomCardPage(cards = emptyList(), nextCursor = null, totalCount = 0)
    }
}
