package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface QuestionCommentRepository : JpaRepository<QuestionCommentEntity, Long> {
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
