package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ClosingQuestionRepository : Repository<QuestionEntity, Long> {
    @Query(
        """
        SELECT q
        FROM QuestionEntity q
        WHERE q.roomId = :roomId
          AND q.targetMemberId = :targetMemberId
          AND q.parentQuestionId IS NULL
          AND q.asked = TRUE
          AND q.deletedAt IS NULL
        ORDER BY q.id ASC
        """,
    )
    fun findAllAskedTopLevelByRoomIdAndTargetMemberId(
        @Param("roomId") roomId: UUID,
        @Param("targetMemberId") targetMemberId: UUID,
    ): List<QuestionEntity>
}
