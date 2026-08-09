package io.plady.moimyeon.core.enums

// 참가 신청의 처리 상태(「룸 참여」 §4.4·§6). 대기 신청만 방장이 수락·반려할 수 있고,
// 처리되면 대기에서 빠져 room_application.pending_member_id 가 NULL 로 풀린다.
enum class RoomApplicationStatus {
    PENDING, // 처리 대기 (방장 판단 전)
    ACCEPTED, // 방장 수락 → 참여자로 등록됨
    REJECTED, // 방장 반려 → 같은 룸 재신청 불가
    WITHDRAWN, // 신청자 철회 → 모집 중이면 재신청 가능

    // 아래 둘은 시스템이 끝낸 신청이다. 반려로 뭉치지 않는 이유는 두 가지다(MOI-394).
    //   1. §4.8 재신청 차단이 REJECTED 만 봐야 한다 — 룸이 취소됐다고 재신청을 막으면 안 된다.
    //   2. §6 이 반려 사유를 숨기므로, 반려로 기록하면 신청자에게 "반려"로만 보인다.
    // 계기를 reject_reason 이 아니라 값으로 나눈 것은 그 컬럼이 방장 전용이기 때문이고,
    // 그래야 화면이 상태 하나로 문구를 고를 수 있다.
    // 실제로 이 상태로 바꾸는 것은 룸 취소(MOI-396)·진행 확정(MOI-398)이 한다.
    ROOM_CANCELED, // 방장이 룸을 접음
    ROOM_CONFIRMED, // 방장이 진행을 확정해 대기가 정리됨
}
