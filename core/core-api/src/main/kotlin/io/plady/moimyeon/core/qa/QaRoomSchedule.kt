package io.plady.moimyeon.core.qa

import io.plady.moimyeon.core.enums.RoomStatus
import java.time.LocalDateTime
import java.util.UUID

data class QaRoomSchedule(
    val roomId: UUID,
    val status: RoomStatus,
    val startAt: LocalDateTime,
)
