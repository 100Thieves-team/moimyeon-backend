package io.plady.moimyeon.core.domain.roomviewer

// 뷰어가 이 룸에서 취할 수 있는 행동(MOI-387 §5). 표시 문구는 내리지 않는다 — 클라이언트가 렌더링한다.
enum class ViewerAction {
    LOGIN_REQUIRED,
    APPLY,
    APPLY_WAITLIST, // 정원이 찼지만 확정 전이라 접수는 된다(「룸 탐색」 §4.5, 20260804)
    VIEW_MY_APPLICATION,
    WITHDRAW_APPLICATION,
    VIEW_MY_ROOM,
    MANAGE_ROOM,
}
