package io.plady.moimyeon.storage.db.core

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface QuestionRepository : JpaRepository<QuestionEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): QuestionEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId: UUID, id: Long): QuestionEntity?

    fun existsByParentQuestionIdAndAuthorMemberIdNotAndDeletedAtIsNull(
        parentQuestionId: Long,
        authorMemberId: UUID,
    ): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findAllByParentQuestionIdAndAuthorMemberIdAndDeletedAtIsNull(
        parentQuestionId: Long,
        authorMemberId: UUID,
    ): List<QuestionEntity>

    fun findByRoomIdAndTargetMemberIdInAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
        roomId: UUID,
        targetMemberIds: Collection<UUID>,
    ): List<QuestionEntity>

    fun findByRoomIdAndTargetMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
        roomId: UUID,
        targetMemberId: UUID,
    ): List<QuestionEntity>

    fun findByRoomIdAndTargetMemberIdAndAskedTrueAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
        roomId: UUID,
        targetMemberId: UUID,
    ): List<QuestionEntity>

    @Query(
        """
        SELECT COUNT(DISTINCT q.authorMemberId)
        FROM QuestionEntity q
        WHERE q.roomId = :roomId
          AND q.targetMemberId = :targetMemberId
          AND q.deletedAt IS NULL
        """,
    )
    fun countDistinctAuthorsByRoomIdAndTargetMemberIdAndDeletedAtIsNull(
        roomId: UUID,
        targetMemberId: UUID,
    ): Long

    @Query(
        """
        SELECT q.authorMemberId AS memberId, COUNT(q) AS count
        FROM QuestionEntity q, AttendanceEntity a, RoomEntity r
        WHERE a.roomId = q.roomId
          AND a.memberId = q.authorMemberId
          AND r.id = q.roomId
          AND r.status = io.plady.moimyeon.core.enums.RoomStatus.COMPLETED
          AND r.deletedAt IS NULL
          AND a.status = io.plady.moimyeon.core.enums.AttendanceStatus.ATTENDED
          AND a.deletedAt IS NULL
          AND q.asked = TRUE
          AND q.deletedAt IS NULL
        GROUP BY q.authorMemberId
        """,
    )
    fun countAskedCompletedActivityByMember(): List<MemberMetricCount>
}
