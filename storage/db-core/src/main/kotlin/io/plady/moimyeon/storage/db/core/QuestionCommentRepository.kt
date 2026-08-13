package io.plady.moimyeon.storage.db.core

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface QuestionCommentRepository : JpaRepository<QuestionCommentEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByIdAndDeletedAtIsNull(id: Long): QuestionCommentEntity?

    @Query(
        """
        SELECT c
        FROM QuestionCommentEntity c
        WHERE c.questionId = :questionId
          AND c.deletedAt IS NULL
          AND (
            :cursorCreatedAt IS NULL
            OR c.createdAt > :cursorCreatedAt
            OR (c.createdAt = :cursorCreatedAt AND c.id > :cursorId)
          )
        ORDER BY c.createdAt ASC, c.id ASC
        """,
    )
    fun findPage(
        @Param("questionId") questionId: Long,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime?,
        @Param("cursorId") cursorId: Long?,
        pageable: Pageable,
    ): List<QuestionCommentEntity>

    @Query(
        """
        SELECT c.authorMemberId AS memberId, COUNT(c) AS count
        FROM QuestionCommentEntity c, QuestionEntity q, AttendanceEntity a, RoomEntity r
        WHERE q.id = c.questionId
          AND a.roomId = q.roomId
          AND a.memberId = c.authorMemberId
          AND r.id = q.roomId
          AND r.status = io.plady.moimyeon.core.enums.RoomStatus.COMPLETED
          AND r.deletedAt IS NULL
          AND a.status = io.plady.moimyeon.core.enums.AttendanceStatus.ATTENDED
          AND a.deletedAt IS NULL
          AND q.deletedAt IS NULL
          AND c.deletedAt IS NULL
        GROUP BY c.authorMemberId
        """,
    )
    fun countCompletedActivityByMember(): List<MemberMetricCount>
}
