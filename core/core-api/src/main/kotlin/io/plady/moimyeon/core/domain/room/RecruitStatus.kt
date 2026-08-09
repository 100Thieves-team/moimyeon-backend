package io.plady.moimyeon.core.domain.room

// 모집 상태는 저장값이 아니라 정원 충족 여부로 계산한다(「룸 탐색」 핵심 결정 4).
// 정원이 찬 룸도 목록에는 나오고 표시만 마감으로 바뀐다 — 확정 전이면 대기 신청이 가능하기 때문이다(「룸 참여」 §4.9).
enum class RecruitStatus {
    RECRUITING,
    CLOSED,
    ;

    companion object {
        fun of(currentParticipants: Int, capacity: RoomCapacity): RecruitStatus = if (currentParticipants >= capacity.max) CLOSED else RECRUITING
    }
}
