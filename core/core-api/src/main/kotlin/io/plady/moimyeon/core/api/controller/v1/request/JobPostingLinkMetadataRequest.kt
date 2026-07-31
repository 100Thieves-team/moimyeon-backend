package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

// 목록에 없는 공고를 링크로 추가하기 전, 링크의 OG 태그를 읽어 공고명 후보와 미리보기를 돌려받는다(「룸 생성」 §4.1).
// 회사는 OG 에서 추출하지 않는다(회사명이 없거나 표기가 흔들리는 링크가 많아) — 회사는 생성(POST /v1/job-postings) 요청에서 지정한다.
data class JobPostingLinkMetadataRequest(
    val url: String,
) {
    // 목킹 단계라 변환할 개념 객체가 아직 없다. 도메인이 붙으면 toXxx() 안으로 옮긴다.
    fun validate() {
        if (url.isBlank() || url.length > URL_MAX_LENGTH) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
    }

    companion object {
        private const val URL_MAX_LENGTH = 2000
    }
}
