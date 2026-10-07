package io.plady.moimyeon.storage.db.core

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface GuestbookPostRepository : JpaRepository<GuestbookPostEntity, Long> {
    // deleted_at 을 거르지 않는 예외적 목록이다 — 삭제된 글도 tombstone 으로 목록에 남아
    // 대화 맥락을 유지해야 한다(「룸 방명록」 §4.3). 가림은 응답 조립이 한다.
    @Query(
        """
        SELECT p
        FROM GuestbookPostEntity p
        WHERE p.roomGuestbookId = :roomGuestbookId
          AND (
            :cursorCreatedAt IS NULL
            OR p.createdAt < :cursorCreatedAt
            OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId)
          )
        ORDER BY p.createdAt DESC, p.id DESC
        """,
    )
    fun findPage(
        @Param("roomGuestbookId") roomGuestbookId: Long,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime?,
        @Param("cursorId") cursorId: Long?,
        pageable: Pageable,
    ): List<GuestbookPostEntity>

    fun findFirstByRoomGuestbookIdAndAuthorMemberIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
        roomGuestbookId: Long,
        authorMemberId: UUID,
    ): GuestbookPostEntity?

    fun findByIdAndRoomGuestbookId(id: Long, roomGuestbookId: Long): GuestbookPostEntity?
}
