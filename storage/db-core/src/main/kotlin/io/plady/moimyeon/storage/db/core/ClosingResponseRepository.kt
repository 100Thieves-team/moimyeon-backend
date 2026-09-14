package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ClosingResponseRepository : JpaRepository<ClosingResponseEntity, Long> {
    fun findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId: UUID, memberId: UUID): ClosingResponseEntity?

    fun findAllByRoomIdAndDeletedAtIsNull(roomId: UUID): List<ClosingResponseEntity>
}
