package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

// 참가 신청 반려(「룸 참여」 §4.4). 방장은 사유 없이 반려할 수 있고, 제공되는 사유 목록에서 골라 넣을 수도 있다.
data class RejectApplicationRequest(
    val reason: String? = null,
) {
    // 반려 사유 정규화. 공백뿐이면 사유 없음(null)으로 보고, 저장 컬럼(reject_reason VARCHAR(50)) 길이를 넘기면 거른다.
    fun toReason(): String? {
        val trimmed = reason?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.length > REASON_MAX_LENGTH) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        return trimmed
    }

    companion object {
        private const val REASON_MAX_LENGTH = 50
    }
}
