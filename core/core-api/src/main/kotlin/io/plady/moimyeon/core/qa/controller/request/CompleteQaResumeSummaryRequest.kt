package io.plady.moimyeon.core.qa.controller.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

data class CompleteQaResumeSummaryRequest(
    val summary: String? = null,
) {
    fun toSummary(): String {
        val value = summary ?: DEFAULT_SUMMARY
        if (value.isBlank() || value.length > MAX_LENGTH) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        return value
    }

    companion object {
        const val DEFAULT_SUMMARY = "[QA] 테스트용 이력서 요약입니다. 백엔드 3년차, Kotlin·Spring 기반 API 개발 경험이 있습니다."
        const val MAX_LENGTH = 1000
    }
}
