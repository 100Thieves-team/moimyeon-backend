package io.plady.moimyeon.core.domain.roomapplication

import java.time.LocalDateTime
import java.util.UUID

data class PendingRoomApplication(
    val id: Long,
    val roomId: UUID,
    val resumeOriginalName: String,
    val appliedAt: LocalDateTime,
)
