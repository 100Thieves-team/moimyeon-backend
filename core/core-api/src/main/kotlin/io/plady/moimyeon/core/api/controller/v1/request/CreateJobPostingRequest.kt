package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

// 목록에 없는 공고를 링크로 즉시 생성한다(「룸 생성」 §4.1). 승인 대기 없이 만들어져 바로 룸 생성에 사용할 수 있다(verified=false).
// 회사는 서비스가 관리하는 목록에서 선택한 companyId 로 받는다(§4.1, 신규 회사 생성은 범위 밖).
// 공고명은 링크 메타데이터(og:title)에서 제안된 값을 사용자가 확인·수정해 확정한 값이다.
data class CreateJobPostingRequest(
    val companyId: Long,
    val url: String,
    val postingName: String,
) {
    // 목킹 단계라 변환할 개념 객체가 아직 없다. 도메인이 붙으면 toXxx() 안으로 옮긴다.
    fun validate() {
        if (url.isBlank() || url.length > URL_MAX_LENGTH) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        if (postingName.isBlank() || postingName.length > POSTING_NAME_MAX_LENGTH) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
    }

    companion object {
        private const val URL_MAX_LENGTH = 2000
        private const val POSTING_NAME_MAX_LENGTH = 100
    }
}
