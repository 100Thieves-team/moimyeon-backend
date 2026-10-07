package io.plady.moimyeon.core.qa

import io.plady.moimyeon.core.enums.RoomStatus
import java.util.UUID

data class QaRoomAutoCompletion(
    val roomId: UUID,
    val completed: Boolean,
    val status: RoomStatus,
)
