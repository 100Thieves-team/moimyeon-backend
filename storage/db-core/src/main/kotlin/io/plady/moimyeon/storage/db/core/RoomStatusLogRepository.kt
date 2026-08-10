package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoomStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoomStatusLogRepository : JpaRepository<RoomStatusLogEntity, Long> {
    fun findByRoomIdAndTransitionTypeAndDeletedAtIsNull(
        roomId: UUID,
        transitionType: RoomStatus,
    ): RoomStatusLogEntity?

    fun countByRoomIdAndTransitionTypeAndDeletedAtIsNull(
        roomId: UUID,
        transitionType: RoomStatus,
    ): Long
}
