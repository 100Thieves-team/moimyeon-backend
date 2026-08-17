package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.RoomCommentCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomCommentsResponse
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.participation.RoomParticipantService
import io.plady.moimyeon.core.domain.roomcomment.RoomCommentCursor
import io.plady.moimyeon.core.domain.roomcomment.RoomCommentService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomCommentFacade(
    private val roomCommentService: RoomCommentService,
    private val roomParticipantService: RoomParticipantService,
    private val memberService: MemberService,
) {
    fun getComments(memberId: UUID, roomId: UUID, cursor: RoomCommentCursor?, size: Int): RoomCommentsResponse {
        // 참여자 게이트는 이 호출 안에 있다 - 통과한 뒤에만 명단·닉네임을 조립한다.
        val listing = roomCommentService.getComments(memberId, roomId, cursor, size)

        val authorIds = listing.page.comments.mapNotNull { it.authorMemberId }.distinct()
        val nicknames = memberService.getMembers(authorIds).associate { it.id to it.nickname.value }
        return RoomCommentsResponse.from(
            listing = listing,
            viewerMemberId = memberId,
            nicknames = nicknames,
            joinedParticipants = roomParticipantService.getJoinedParticipants(roomId),
        )
    }

    fun leaveComment(memberId: UUID, roomId: UUID, content: String): RoomCommentCreatedResponse {
        val commentId = roomCommentService.leaveComment(memberId, roomId, content)
        // 쓰기는 id 만 반환한다 - 작성 시각은 재조회로 조립한다(layers.md).
        val comment = roomCommentService.getComment(commentId)
        return RoomCommentCreatedResponse(commentId = comment.id, createdAt = comment.createdAt)
    }
}
