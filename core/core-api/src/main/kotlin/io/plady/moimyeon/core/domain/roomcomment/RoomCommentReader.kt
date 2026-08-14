package io.plady.moimyeon.core.domain.roomcomment

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.GuestbookPostEntity
import io.plady.moimyeon.storage.db.core.GuestbookPostRepository
import io.plady.moimyeon.storage.db.core.RoomGuestbookRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomCommentReader(
    private val roomGuestbookRepository: RoomGuestbookRepository,
    private val guestbookPostRepository: GuestbookPostRepository,
) {
    fun getPage(roomId: UUID, cursor: RoomCommentCursor?, size: Int): RoomCommentPage {
        // 방명록 행은 첫 글 작성 때 lazy 생성되므로(RoomCommentManager) 행이 없으면 글이 없는 것이다.
        val guestbook = roomGuestbookRepository.findByRoomIdAndDeletedAtIsNull(roomId)
            ?: return RoomCommentPage(comments = emptyList(), nextCursor = null)

        val entities = guestbookPostRepository.findPage(
            roomGuestbookId = guestbook.id,
            cursorCreatedAt = cursor?.createdAt,
            cursorId = cursor?.id,
            pageable = PageRequest.of(0, size + 1),
        )
        val comments = entities.take(size).map { it.toDomain() }
        val nextCursor = comments.lastOrNull()
            ?.takeIf { entities.size > size }
            ?.let { RoomCommentCursor(it.createdAt, it.id) }
        return RoomCommentPage(comments, nextCursor)
    }

    fun getComment(commentId: Long): RoomComment {
        return requireFound(
            guestbookPostRepository.findById(commentId).orElse(null)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_COMMENT_NOT_FOUND,
        ).toDomain()
    }

    private fun GuestbookPostEntity.toDomain(): RoomComment = if (isDeleted()) {
        RoomComment(id = id, authorMemberId = null, content = null, createdAt = createdAt, isDeleted = true)
    } else {
        RoomComment(id = id, authorMemberId = authorMemberId, content = content, createdAt = createdAt, isDeleted = false)
    }
}
