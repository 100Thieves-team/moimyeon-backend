package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface QuestionRepository : JpaRepository<QuestionEntity, Long> {
    fun findByRoomIdAndTargetMemberIdInAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
        roomId: UUID,
        targetMemberIds: Collection<UUID>,
    ): List<QuestionEntity>

    fun findByRoomIdAndTargetMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
        roomId: UUID,
        targetMemberId: UUID,
    ): List<QuestionEntity>
}
