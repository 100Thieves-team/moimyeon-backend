package io.plady.moimyeon.core.domain.roomcomment

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.GuestbookPostEntity
import io.plady.moimyeon.storage.db.core.GuestbookPostRepository
import io.plady.moimyeon.storage.db.core.RoomGuestbookEntity
import io.plady.moimyeon.storage.db.core.RoomGuestbookRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class RoomCommentManager(
    private val roomGuestbookRepository: RoomGuestbookRepository,
    private val guestbookPostRepository: GuestbookPostRepository,
    private val windowReader: RoomCommentWindowReader,
) {
    // 읽기 전용 판정이 쓰기와 같은 커밋 경계 안에 있어야 "전환 순간의 등록"(§4.6)이 확정적으로
    // 거부된다. 방명록 행 잠금이 같은 룸의 작성을 직렬화해 lazy 생성과 멱등 판정(D10)을 함께 덮는다.
    // ⚠️ 이 잠금이 빠져도 순차 테스트는 전부 초록이다(레이스는 재현하지 않는다 - testing.md).
    //    지우면 더블클릭 두 요청이 각자 "직전 글 없음"을 보고 같은 글을 두 번 만든다.
    @Transactional
    fun post(roomId: UUID, authorMemberId: UUID, content: String, now: LocalDateTime): Long {
        requireBusiness(windowReader.getWindow(roomId, now).writable, CoreErrorType.ROOM_COMMENT_READ_ONLY)

        val guestbook = getOrCreateGuestbook(roomId)
        val last = guestbookPostRepository
            .findFirstByRoomGuestbookIdAndAuthorMemberIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                guestbook.id,
                authorMemberId,
            )
        if (last != null && RoomCommentDuplicate.isDuplicate(last.content, last.createdAt, content, now)) {
            return last.id
        }
        return guestbookPostRepository.save(
            GuestbookPostEntity(
                roomGuestbookId = guestbook.id,
                authorMemberId = authorMemberId,
                content = content,
            ),
        ).id
    }

    @Transactional
    fun remove(roomId: UUID, authorMemberId: UUID, commentId: Long, now: LocalDateTime) {
        // 전환 후 목록은 기록이다 - 읽기 전용이면 삭제도 막는다(D11).
        requireBusiness(windowReader.getWindow(roomId, now).writable, CoreErrorType.ROOM_COMMENT_READ_ONLY)

        val guestbook = roomGuestbookRepository.findByRoomIdAndDeletedAtIsNull(roomId)
        val post = requireFound(
            guestbook?.let { guestbookPostRepository.findByIdAndRoomGuestbookId(commentId, it.id) },
            CoreErrorType.ROOM_COMMENT_NOT_FOUND,
        )
        requireBusiness(post.authorMemberId == authorMemberId, CoreErrorType.ROOM_COMMENT_NOT_MINE)
        // 이미 삭제된 글이면 delete 가 시각을 덮지 않는다(AbstractEntity) - 재삭제는 그대로 성공이다.
        post.delete(now)
    }

    private fun getOrCreateGuestbook(roomId: UUID): RoomGuestbookEntity {
        roomGuestbookRepository.findForUpdateByRoomIdAndDeletedAtIsNull(roomId)?.let { return it }
        return try {
            roomGuestbookRepository.saveAndFlush(RoomGuestbookEntity(roomId))
        } catch (e: DataIntegrityViolationException) {
            // 룸당 1개 유니크(uk_room_guestbook_room_active)가 동시 lazy 생성의 최종 방어선이다.
            // 진 쪽은 이긴 행을 다시 잡는다. 재조회도 비면 다른 무결성 위반이므로 오인하지 않고 전파한다.
            roomGuestbookRepository.findForUpdateByRoomIdAndDeletedAtIsNull(roomId) ?: throw e
        }
    }
}
