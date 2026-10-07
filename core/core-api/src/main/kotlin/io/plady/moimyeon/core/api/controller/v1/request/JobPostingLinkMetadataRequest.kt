package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

// 회사는 OG 에서 추출하지 않는다(회사명이 없거나 표기가 흔들리는 링크가 많아) — 사용자가 고른 companyId 를 함께 받아
// "이 링크는 그 회사의 공고"라고 일단 가정한다(링크와 회사의 실제 일치는 검증하지 않는다). 회사의 실존 검증은 생성 요청에서 한다.
data class JobPostingLinkMetadataRequest(
    val companyId: Long,
    val url: String,
) {
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
