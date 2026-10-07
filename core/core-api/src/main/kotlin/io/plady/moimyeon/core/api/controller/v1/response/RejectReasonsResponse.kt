package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.room.RejectReason

// 반려 모달(D·03)의 사유 선택지. 라벨은 서버 소유이고 enum 선언 순서가 곧 화면 순서다.
// "사유 없이 반려할게요"는 선택지가 아니라 반려 요청의 null 이므로 목록에 없다.
data class RejectReasonsResponse(
    val reasons: List<CodeLabelResponse>,
) {
    companion object {
        fun of(): RejectReasonsResponse {
            return RejectReasonsResponse(
                reasons = RejectReason.entries.map { CodeLabelResponse(it.name, it.toLabel()) },
            )
        }
    }
}

private fun RejectReason.toLabel(): String {
    return when (this) {
        RejectReason.ROLE_MISMATCH -> "직무·면접 단계가 맞지 않아요"
        RejectReason.CAPACITY_FILLED -> "정원을 다른 분들로 채웠어요"
        RejectReason.DIRECTION_MISMATCH -> "설명한 준비 방향과 맞지 않아요"
    }
}
