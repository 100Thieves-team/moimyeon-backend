package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

// 목록에 없는 공고를 링크로 추가하기 전, 링크의 OG 태그를 읽어 공고명 후보와 미리보기를 돌려받는다(「룸 생성」 §4.1).
// 회사는 OG 에서 추출하지 않는다(회사명이 없거나 표기가 흔들리는 링크가 많아) — 사용자가 고른 companyId 를 함께 받아
// "이 링크는 그 회사의 공고"라고 일단 가정한다(링크와 회사의 실제 일치는 검증하지 않는다). 회사의 실존 검증은 생성 요청에서 한다.
data class JobPostingLinkMetadataRequest(
    val companyId: Long,
    val url: String,
) {
    // 형식 검증 후 정규화한(trim) URL 을 돌려준다. companyId 가 양수가 아니거나 url 이 http/https 가 아니거나
    // 비었거나 길이를 넘으면 400(E400). companyId 의 실존 여부는 미리보기 단계에서 검증하지 않는다(생성에서 검증).
    fun toUrl(): String {
        val trimmed = url.trim()
        if (companyId <= 0) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        if (trimmed.isBlank() || trimmed.length > URL_MAX_LENGTH || !trimmed.isHttpUrl()) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        return trimmed
    }

    companion object {
        private const val URL_MAX_LENGTH = 2000
    }
}

internal fun String.isHttpUrl(): Boolean = startsWith("http://") || startsWith("https://")
