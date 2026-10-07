package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoomStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoomStatusLogRepository : JpaRepository<RoomStatusLogEntity, Long> {
    fun existsByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId: UUID, transitionType: RoomStatus): Boolean

    fun findFirstByRoomIdAndTransitionTypeAndDeletedAtIsNullOrderByOccurredAtDescIdDesc(
        roomId: UUID,
        transitionType: RoomStatus,
    ): RoomStatusLogEntity?

    fun findByRoomIdAndTransitionTypeAndDeletedAtIsNull(
        roomId: UUID,
        transitionType: RoomStatus,
    ): RoomStatusLogEntity? = findFirstByRoomIdAndTransitionTypeAndDeletedAtIsNullOrderByOccurredAtDescIdDesc(
        roomId,
        transitionType,
    )

    fun countByRoomIdAndTransitionTypeAndDeletedAtIsNull(
        roomId: UUID,
        transitionType: RoomStatus,
    ): Long
}
