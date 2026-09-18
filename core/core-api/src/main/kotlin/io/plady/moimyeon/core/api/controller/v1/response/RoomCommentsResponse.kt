package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.api.controller.v1.request.RoomCommentCursorToken
import io.plady.moimyeon.core.domain.participation.JoinedParticipant
import io.plady.moimyeon.core.domain.roomcomment.RoomCommentListing
import java.time.LocalDateTime
import java.util.UUID

data class RoomCommentsResponse(
    val comments: List<RoomCommentResponse>,
    val writable: Boolean,
    val readOnlyAt: LocalDateTime?,
    val nextCursor: String?,
) {
    companion object {
        fun from(
            listing: RoomCommentListing,
            viewerMemberId: UUID,
            nicknames: Map<UUID, String>,
            joinedParticipants: List<JoinedParticipant>,
        ): RoomCommentsResponse {
            val joinedMemberIds = joinedParticipants.map { it.memberId }.toSet()
            // 방장 뱃지는 현재 방장 기준(F6) - 위임되면 옛 방장의 글에서 뱃지가 사라진다.
            val hostMemberId = joinedParticipants.firstOrNull { it.isHost }?.memberId

            return RoomCommentsResponse(
                comments = listing.page.comments.map { comment ->
                    val authorMemberId = comment.authorMemberId
                    if (comment.isDeleted || authorMemberId == null) {
                        // tombstone - "삭제된 댓글입니다" 문구는 FE 소유. 내 글이었는지도 가린다.
                        RoomCommentResponse(
                            commentId = comment.id,
                            isDeleted = true,
                            author = null,
                            content = null,
                            createdAt = comment.createdAt,
                            isMine = false,
                        )
                    } else {
                        RoomCommentResponse(
                            commentId = comment.id,
                            isDeleted = false,
                            author = RoomCommentAuthorResponse(
                                memberId = authorMemberId,
                                nickname = nicknames[authorMemberId] ?: "탈퇴한 회원",
                                isHost = authorMemberId == hostMemberId,
                                hasLeft = authorMemberId !in joinedMemberIds,
                            ),
                            content = comment.content,
                            createdAt = comment.createdAt,
                            isMine = authorMemberId == viewerMemberId,
                        )
                    }
                },
                writable = listing.window.writable,
                readOnlyAt = listing.window.readOnlyAt,
                nextCursor = listing.page.nextCursor?.let(RoomCommentCursorToken::encode),
            )
        }
    }
}

data class RoomCommentResponse(
    val commentId: Long,
    val isDeleted: Boolean,
    val author: RoomCommentAuthorResponse?,
    val content: String?,
    val createdAt: LocalDateTime,
    val isMine: Boolean,
)

data class RoomCommentAuthorResponse(
    val memberId: UUID,
    val nickname: String,
    val isHost: Boolean,
    val hasLeft: Boolean,
)
