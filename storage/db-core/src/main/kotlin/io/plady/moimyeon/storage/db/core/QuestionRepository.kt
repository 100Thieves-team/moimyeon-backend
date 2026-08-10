package io.plady.moimyeon.storage.db.core

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
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
}
