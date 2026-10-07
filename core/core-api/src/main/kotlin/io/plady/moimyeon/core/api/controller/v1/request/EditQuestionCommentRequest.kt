package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.util.UUID

data class EditQuestionCommentRequest(
    val roomId: UUID,
    val intervieweeMemberId: UUID,
    val questionId: Long,
    val content: String,
) {
    fun toContent(): String {
        if (content.isBlank()) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        return content
    }
}
