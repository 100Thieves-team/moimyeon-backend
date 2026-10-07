package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.util.UUID

data class CreateQuestionRequest(
    val targetMemberId: UUID,
    val content: String,
) {
    fun toContent(): String {
        if (content.isBlank() || content.length > CONTENT_MAX_LENGTH) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        return content
    }

    private companion object {
        const val CONTENT_MAX_LENGTH = 500
    }
}
