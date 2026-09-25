package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import java.util.UUID

internal fun RoomStatusLogRepository.wasPreviouslyConfirmed(roomId: UUID): Boolean = existsByRoomIdAndTransitionTypeAndDeletedAtIsNull(
    roomId,
    RoomStatus.CONFIRMED,
)
