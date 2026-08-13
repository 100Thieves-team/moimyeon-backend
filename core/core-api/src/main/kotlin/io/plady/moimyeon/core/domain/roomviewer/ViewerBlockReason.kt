package io.plady.moimyeon.core.domain.roomviewer

// 뷰어가 이 룸에서 할 수 있는 것이 하나도 없는 이유(MOI-387 §5).
//
// ⚠️ 정원 관련 값이 없는 것은 누락이 아니다. 정원 도달은 APPLY 냐 APPLY_WAITLIST 냐의
// 분기일 뿐 차단 조건이 아니다(§5 불변식 3). 값을 추가하기 전에 그 결정을 먼저 뒤집어야 한다.
enum class ViewerBlockReason {
    ROOM_CONFIRMED,
    ROOM_CANCELED,
    ROOM_COMPLETED,

    // 진행 중인 룸도 여기로 온다 — IN_PROGRESS 면 일정이 이미 시작됐다(D2).
    // 방장에게 "왜 확정이 안 되나"를 말하는 RoomConfirmationBlockReason.ROOM_IN_PROGRESS 와는 축이 다르다.
    SCHEDULE_PASSED,

    APPLICATION_REJECTED,
    REMOVED_FROM_ROOM,
    MEMBER_SUSPENDED,
    PARTICIPATION_SLOT_EXCEEDED,
    APPLICATION_LIMIT_EXCEEDED,
}
