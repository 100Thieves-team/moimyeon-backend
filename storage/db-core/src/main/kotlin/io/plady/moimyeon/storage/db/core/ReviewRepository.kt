package io.plady.moimyeon.storage.db.core

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface ReviewRepository : JpaRepository<ReviewEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByIdAndDeletedAtIsNull(reviewId: Long): ReviewEntity?

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

    @Query(
        """
        SELECT DISTINCT r
        FROM ReviewEntity r LEFT JOIN FETCH r.tags
        WHERE r.roomId = :roomId
          AND r.authorMemberId = :authorMemberId
          AND r.deletedAt IS NULL
        ORDER BY r.id ASC
        """,
    )
    fun findAllWithTagsByRoomIdAndAuthorMemberIdAndDeletedAtIsNull(
        @Param("roomId") roomId: UUID,
        @Param("authorMemberId") authorMemberId: UUID,
    ): List<ReviewEntity>

    fun findByRoomIdInAndAuthorMemberIdAndDeletedAtIsNull(
        roomIds: Collection<UUID>,
        authorMemberId: UUID,
    ): List<ReviewEntity>

    @Query(
        """
        SELECT r
        FROM ReviewEntity r
        WHERE r.targetMemberId = :memberId
          AND r.visibleAt <= :now
          AND r.hiddenAt IS NULL
          AND r.deletedAt IS NULL
          AND (:lastReviewId IS NULL OR r.id < :lastReviewId)
        ORDER BY r.id DESC
        """,
    )
    fun findVisibleReceivedReviewPage(
        @Param("memberId") memberId: UUID,
        @Param("now") now: LocalDateTime,
        @Param("lastReviewId") lastReviewId: Long?,
        pageable: Pageable,
    ): List<ReviewEntity>

    @Query(
        """
        SELECT DISTINCT r
        FROM ReviewEntity r LEFT JOIN FETCH r.tags
        WHERE r.id IN :reviewIds
        """,
    )
    fun findAllWithTagsByIdIn(@Param("reviewIds") reviewIds: Collection<Long>): List<ReviewEntity>

    @Query(
        """
        SELECT COUNT(r)
        FROM ReviewEntity r
        WHERE r.targetMemberId = :memberId
          AND r.visibleAt <= :now
          AND r.hiddenAt IS NULL
          AND r.deletedAt IS NULL
        """,
    )
    fun countVisibleReceivedReviews(
        @Param("memberId") memberId: UUID,
        @Param("now") now: LocalDateTime,
    ): Long

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

    @Query(
        """
        select new io.plady.moimyeon.storage.db.core.ReviewNotificationCandidate(
                   r.id, r.roomId, r.authorMemberId, r.targetMemberId, r.hiddenAt)
          from ReviewEntity r
         where r.notifiedAt is null
           and r.visibleAt <= :now
           and r.deletedAt is null
         order by r.visibleAt asc, r.id asc
        """,
    )
    fun findNotificationCandidates(
        @Param("now") now: LocalDateTime,
        pageable: Pageable,
    ): List<ReviewNotificationCandidate>

    // 1 을 받은 쪽만 알린다. 후보 조회 뒤 지워진 후기를 거르려고 조건을 다시 건다.
    // @UpdateTimestamp 는 벌크 UPDATE 에서 돌지 않아 updatedAt 을 직접 넣는다.
    @Modifying(flushAutomatically = true)
    @Query(
        """
        update ReviewEntity r
           set r.notifiedAt = :now,
               r.updatedAt = :now
         where r.id = :reviewId
           and r.notifiedAt is null
           and r.deletedAt is null
           and r.visibleAt <= :now
        """,
    )
    fun markNotified(
        @Param("reviewId") reviewId: Long,
        @Param("now") now: LocalDateTime,
    ): Int
}
