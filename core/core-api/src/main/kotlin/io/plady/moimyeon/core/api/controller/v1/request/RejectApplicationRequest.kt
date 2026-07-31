package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

// 참가 신청 반려(「룸 참여」 §4.4). 방장은 사유 없이 반려할 수 있고, 제공되는 사유 목록에서 골라 넣을 수도 있다.
data class RejectApplicationRequest(
    val reason: String? = null,
) {
    // 목킹 단계라 변환할 개념 객체가 아직 없다. 도메인이 붙으면 toXxx() 안으로 옮긴다.
    fun validate() {
        if (reason != null && reason.length > REASON_MAX_LENGTH) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
    }

    companion object {
        private const val REASON_MAX_LENGTH = 200
    }
}
