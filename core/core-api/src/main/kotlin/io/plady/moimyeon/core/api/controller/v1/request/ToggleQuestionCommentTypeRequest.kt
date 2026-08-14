package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.util.UUID

data class ToggleQuestionCommentTypeRequest(
    val roomId: UUID,
    val intervieweeMemberId: UUID,
    val questionId: Long,
    val type: QuestionCommentType,
) {
    fun toType(): QuestionCommentType {
        if (type == QuestionCommentType.MEMO) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        return type
    }
}
