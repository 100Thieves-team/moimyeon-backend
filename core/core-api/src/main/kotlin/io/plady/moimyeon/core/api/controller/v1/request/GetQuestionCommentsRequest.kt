package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.question.QuestionCommentCursor
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.time.LocalDateTime
import java.util.UUID

data class GetQuestionCommentsRequest(
    val roomId: UUID,
    val intervieweeMemberId: UUID,
    val questionId: Long,
    val cursorCreatedAt: LocalDateTime? = null,
    val cursorId: Long? = null,
) {
    fun toCursor(): QuestionCommentCursor? {
        if ((cursorCreatedAt == null) != (cursorId == null)) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        return cursorCreatedAt?.let { QuestionCommentCursor(it, requireNotNull(cursorId)) }
    }
}
