package io.plady.moimyeon.core.domain.roomviewer

// 뷰어 관계 판정의 결과. 목록·상세가 같은 객체를 싣는다(MOI-387 §5).
//
// 불리언 플래그(canApply·isHost…)를 쓰지 않는 이유는 §4 우선순위가 클라이언트로 새어 나가기 때문이다.
// 우선순위의 소유자는 서버 한 곳이어야 한다.
data class RoomViewer(
    val relation: ViewerRelation,
    // 순서가 계약이다 — 첫 원소가 주 버튼이다(§5 불변식 2).
    val actions: List<ViewerAction>,
    val blockReason: ViewerBlockReason?,
) {
    init {
        // §5 불변식 1. 차단은 액션 종류가 아니라 "가능한 액션이 하나도 없는 상태"다 —
        // 방장이 취소된 룸을 보면 actions=[MANAGE_ROOM]·blockReason=null 이라 차단이 아니다.
        //
        // 서버가 스스로 만든 값의 자기 정합성이므로 위반은 사용자 입력 오류가 아니라 버그다.
        // requireBusiness 가 아니라 require 인 이유가 그것이다.
        require(actions.isEmpty() == (blockReason != null)) {
            "actions 가 비어 있는 것과 blockReason 이 있는 것은 일치해야 합니다. actions=$actions, blockReason=$blockReason"
        }
    }
}
