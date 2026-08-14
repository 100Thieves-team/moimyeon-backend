package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.roundfeedback.RoundQuestionRecord
import java.time.LocalDateTime

data class RoundQuestionRecordsResponse(
    val records: List<RoundQuestionRecordResponse>,
) {
    companion object {
        fun from(records: List<RoundQuestionRecord>): RoundQuestionRecordsResponse {
            return RoundQuestionRecordsResponse(
                records.map { record ->
                    RoundQuestionRecordResponse(
                        questionId = record.questionId,
                        questionContent = record.questionContent,
                        comments = record.comments.map { comment ->
                            RoundQuestionCommentResponse(
                                commentId = comment.id,
                                type = comment.type.name,
                                content = comment.content,
                                createdAt = comment.createdAt,
                            )
                        },
                    )
                },
            )
        }
    }
}

data class RoundQuestionRecordResponse(
    val questionId: Long,
    val questionContent: String,
    val comments: List<RoundQuestionCommentResponse>,
)

data class RoundQuestionCommentResponse(
    val commentId: Long,
    val type: String,
    val content: String,
    val createdAt: LocalDateTime,
)
