package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.util.UUID

data class CreateQuestionCommentRequest(
    val roomId: UUID,
    val intervieweeMemberId: UUID,
    val questionId: Long,
    val type: QuestionCommentType,
    val content: String,
) {
    fun toType(): QuestionCommentType {
        if (type != QuestionCommentType.MEMO) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        return type
    }

    fun toContent(): String {
        if (content.isBlank()) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        return content
    }
}
