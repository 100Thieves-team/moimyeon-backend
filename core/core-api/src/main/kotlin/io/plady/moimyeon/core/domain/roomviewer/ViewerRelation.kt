package io.plady.moimyeon.core.domain.roomviewer

// 뷰어와 룸 사이의 관계(MOI-387 §1). 배지(`참여 중`·`내가 만든 룸`)가 이 값으로 그려진다.
//
// actions 와 따로 두는 이유는 배지가 행위가 아니기 때문이다 — actions=[VIEW_MY_ROOM] 에서
// 배지를 역산하면 그 역산이 다시 클라이언트 로직이 된다.
enum class ViewerRelation {
    ANONYMOUS, // 비로그인
    NONE, // 로그인했지만 이 룸과 관계가 없다
    APPLIED, // 신청이 대기 중
    WITHDRAWN, // 본인이 철회했다
    REJECTED, // 방장이 반려했다
    REMOVED, // 방장이 내보냈다

    // 시스템이 끝낸 신청. WITHDRAWN·REJECTED 와 같은 축(신청이 어떻게 끝났나)이면서
    // 둘과 달리 본인도 방장도 원인이 아니다. 룸 취소·확정과 참여 슬롯 초과(MOI-427)가 여기 온다.
    // 사유는 blockReason 이 말하므로 원인별로 값을 나누지 않는다(D1).
    APPLICATION_CLOSED,

    PARTICIPANT, // 참여 중
    HOST, // 방장
}
