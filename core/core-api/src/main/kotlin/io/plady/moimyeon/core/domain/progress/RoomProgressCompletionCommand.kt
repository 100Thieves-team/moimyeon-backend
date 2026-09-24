package io.plady.moimyeon.core.domain.progress

import java.time.LocalDateTime
import java.util.UUID

data class RoomProgressCompletionCommand(
    val roomId: UUID,
    val completedByMemberId: UUID,
    val completedAt: LocalDateTime,
)
