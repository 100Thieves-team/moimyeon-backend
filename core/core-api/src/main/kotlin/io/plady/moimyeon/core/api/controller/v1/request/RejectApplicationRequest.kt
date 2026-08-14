package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.room.RejectReason
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

// 참가 신청 반려(「룸 참여」 §4.4). 방장은 사유 없이 반려할 수 있고,
// 사유를 담으면 GET /v1/rooms/reject-reasons 가 내려주는 코드 중 하나여야 한다.
data class RejectApplicationRequest(
    val reason: String? = null,
) {
    // null 만 사유 없음이다. 그 외 문자열은 코드 정확 일치가 아니면 전부 400 —
    // "사유 없음"의 표현을 null 하나로 유지한다(MOI-451 D9).
    fun toReason(): RejectReason? {
        if (reason == null) return null
        return RejectReason.entries.find { it.name == reason }
            ?: throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
    }
}
