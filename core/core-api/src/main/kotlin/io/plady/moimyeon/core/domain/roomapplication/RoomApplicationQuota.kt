package io.plady.moimyeon.core.domain.roomapplication

// 대기 신청 한도(「룸 참여」 §4.1) — 한 사람이 동시에 걸어 둘 수 있는 대기 신청 수.
//
// 규칙을 여기 하나가 갖는다. 막는 쪽(RoomApplicationSubmissionManager)과 묻는 쪽
// (RoomApplicationSubmissionFinder.hasAvailableQuota, 룸 상세가 쓴다 — MOI-387)이 같은 수를 봐야
// 화면이 "신청할 수 있다"고 안내한 뒤 서버가 거부하는 일이 없다.
//
// 참여 슬롯(ParticipationSlot)과 다른 축이다 — 이쪽은 대기 중인 신청 수, 그쪽은 참여 중인 룸 수다.
object RoomApplicationQuota {
    // PRD 는 회원별 개인화를 예고하지만 아직 상수다.
    const val MAX_PENDING: Long = 3

    fun isAvailable(pendingCount: Long): Boolean = pendingCount < MAX_PENDING
}
