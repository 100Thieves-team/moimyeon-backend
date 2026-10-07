package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

data class CreateRoomCommentRequest(
    val content: String,
) {
    // trim 결과가 저장된다 - 같은 내용의 재시도 멱등 판정(D10)이 공백 차이로 갈리지 않게.
    fun toContent(): String {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.length > CONTENT_MAX_LENGTH) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        return trimmed
    }

    companion object {
        private const val CONTENT_MAX_LENGTH = 1_000
    }
}
