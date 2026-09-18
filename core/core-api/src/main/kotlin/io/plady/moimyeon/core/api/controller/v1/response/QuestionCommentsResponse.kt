package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.question.QuestionCommentCursor
import io.plady.moimyeon.core.domain.question.QuestionCommentPage
import java.time.LocalDateTime
import java.util.UUID

data class QuestionCommentsResponse(
    val comments: List<QuestionCommentResponse>,
    val nextCursor: QuestionCommentCursorResponse?,
) {
    companion object {
        fun from(
            page: QuestionCommentPage,
            viewerMemberId: UUID,
            nicknames: Map<UUID, String>,
        ): QuestionCommentsResponse = QuestionCommentsResponse(
            comments = page.comments.map { comment ->
                QuestionCommentResponse(
                    commentId = comment.id,
                    author = QuestionCommentAuthorResponse(
                        memberId = comment.authorMemberId,
                        nickname = nicknames[comment.authorMemberId] ?: "탈퇴한 회원",
                        mine = comment.authorMemberId == viewerMemberId,
                    ),
                    type = comment.type.name,
                    content = comment.content,
                    createdAt = comment.createdAt,
                )
            },
            nextCursor = page.nextCursor?.let(QuestionCommentCursorResponse::from),
        )
    }
}

data class QuestionCommentResponse(
    val commentId: Long,
    val author: QuestionCommentAuthorResponse,
    val type: String,
    val content: String,
    val createdAt: LocalDateTime,
)

data class QuestionCommentAuthorResponse(
    val memberId: UUID,
    val nickname: String,
    val mine: Boolean,
)

data class QuestionCommentCursorResponse(
    val createdAt: LocalDateTime,
    val id: Long,
) {
    companion object {
        fun from(cursor: QuestionCommentCursor): QuestionCommentCursorResponse {
            return QuestionCommentCursorResponse(cursor.createdAt, cursor.id)
        }
    }
}
