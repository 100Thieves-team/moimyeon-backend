package io.plady.moimyeon.core.domain.roomcomment

import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

// 세 흐름 모두 룸 참여자 게이트(E1419)가 맨 앞이다 - 제3자·신청자·회수자·없는 룸은 존재 여부조차
// 알 수 없다(「룸 방명록」 §3, 명부와 같은 게이트 재사용).
@Service
class RoomCommentService(
    private val participationValidator: ParticipationValidator,
    private val windowReader: RoomCommentWindowReader,
    private val commentReader: RoomCommentReader,
    private val commentManager: RoomCommentManager,
    private val clock: Clock,
) {
    fun getComments(viewerMemberId: UUID, roomId: UUID, cursor: RoomCommentCursor?, size: Int): RoomCommentListing {
        participationValidator.validateParticipant(roomId, viewerMemberId)
        return RoomCommentListing(
            window = windowReader.getWindow(roomId, now()),
            page = commentReader.getPage(roomId, cursor, size),
        )
    }

    fun getComment(commentId: Long): RoomComment = commentReader.getComment(commentId)

    fun leaveComment(authorMemberId: UUID, roomId: UUID, content: String): Long {
        participationValidator.validateParticipant(roomId, authorMemberId)
        return commentManager.post(roomId, authorMemberId, content, now())
    }

    fun deleteComment(memberId: UUID, roomId: UUID, commentId: Long) {
        participationValidator.validateParticipant(roomId, memberId)
        commentManager.remove(roomId, memberId, commentId, now())
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock)
}
