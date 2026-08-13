package io.plady.moimyeon.storage.db.core

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface ReviewRepository : JpaRepository<ReviewEntity, Long> {
    fun findByIdAndDeletedAtIsNull(reviewId: Long): ReviewEntity?

    fun existsByRoomIdAndAuthorMemberIdAndTargetMemberIdAndDeletedAtIsNull(
        roomId: UUID,
        authorMemberId: UUID,
        targetMemberId: UUID,
    ): Boolean

    fun findByRoomIdAndAuthorMemberIdAndDeletedAtIsNull(
        roomId: UUID,
        authorMemberId: UUID,
    ): List<ReviewEntity>

    fun findByRoomIdInAndAuthorMemberIdAndDeletedAtIsNull(
        roomIds: Collection<UUID>,
        authorMemberId: UUID,
    ): List<ReviewEntity>

    @Query(
        """
        SELECT DISTINCT r
        FROM ReviewEntity r LEFT JOIN FETCH r.tags
        WHERE r.targetMemberId = :memberId
          AND r.visibleAt <= :now
          AND r.hiddenAt IS NULL
          AND r.deletedAt IS NULL
        ORDER BY r.visibleAt DESC, r.id DESC
        """,
    )
    fun findVisibleReceivedReviews(
        @Param("memberId") memberId: UUID,
        @Param("now") now: LocalDateTime,
    ): List<ReviewEntity>

    @Query(
        """
        SELECT tag AS label, COUNT(tag) AS count
        FROM ReviewEntity r JOIN r.tags tag
        WHERE r.targetMemberId = :memberId
          AND r.visibleAt <= :now
          AND r.hiddenAt IS NULL
          AND r.deletedAt IS NULL
        GROUP BY tag
        ORDER BY COUNT(tag) DESC, tag ASC
        """,
    )
    fun findRepresentativeTags(
        @Param("memberId") memberId: UUID,
        @Param("now") now: LocalDateTime,
        pageable: Pageable,
    ): List<TagMetricCount>
}
