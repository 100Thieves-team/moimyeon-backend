package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AnswerSummaryRepository : JpaRepository<AnswerSummaryEntity, Long> {
    @Query(
        """
        SELECT s.authorMemberId AS memberId, COUNT(s) AS count
        FROM AnswerSummaryEntity s, QuestionEntity q, AttendanceEntity a, RoomEntity r
        WHERE q.id = s.questionId
          AND a.roomId = q.roomId
          AND a.memberId = s.authorMemberId
          AND r.id = q.roomId
          AND r.status = io.plady.moimyeon.core.enums.RoomStatus.COMPLETED
          AND r.deletedAt IS NULL
          AND a.status = io.plady.moimyeon.core.enums.AttendanceStatus.ATTENDED
          AND a.deletedAt IS NULL
          AND q.deletedAt IS NULL
          AND s.deletedAt IS NULL
        GROUP BY s.authorMemberId
        """,
    )
    fun countCompletedActivityByMember(): List<MemberMetricCount>
}
