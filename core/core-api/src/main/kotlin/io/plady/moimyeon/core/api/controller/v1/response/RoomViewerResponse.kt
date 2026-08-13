package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.roomviewer.RoomViewer

// 조회자와 룸의 관계·행동·차단 사유(「룸 탐색」 §4.5, MOI-387 §5).
// 목록과 상세가 같은 객체를 싣는다 — 클라이언트가 렌더러를 하나만 만들면 된다.
//
// 표시 문구는 내리지 않는다. 불리언 플래그(canApply·isHost…)도 쓰지 않는다 —
// 판정 우선순위가 클라이언트로 새어 나가면 서버와 화면이 서로 다른 답을 하게 된다.
data class RoomViewerResponse(
    val relation: String, // ViewerRelation
    // 순서가 계약이다. 첫 원소가 주 버튼이다.
    val actions: List<String>, // ViewerAction
    // actions 가 비어 있을 때만 값이 있다. 정원 도달은 사유가 아니다 — APPLY_WAITLIST 로 접수된다.
    val blockReason: String?, // ViewerBlockReason
) {
    companion object {
        fun from(viewer: RoomViewer): RoomViewerResponse = RoomViewerResponse(
            relation = viewer.relation.name,
            actions = viewer.actions.map { it.name },
            blockReason = viewer.blockReason?.name,
        )
    }
}
