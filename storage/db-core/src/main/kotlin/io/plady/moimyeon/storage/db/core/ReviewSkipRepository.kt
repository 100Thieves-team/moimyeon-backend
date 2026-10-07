package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReviewSkipRepository : JpaRepository<ReviewSkipEntity, Long> {
    fun existsByRoomIdAndAuthorMemberIdAndTargetMemberId(
        roomId: UUID,
        authorMemberId: UUID,
        targetMemberId: UUID,
    ): Boolean
}
