package io.plady.moimyeon.core.domain.progress

import java.time.LocalDateTime
import java.util.UUID

data class RoomProgressStartCommand(
    val roomId: UUID,
    val startedByMemberId: UUID,
    val attendances: List<Attendance>,
    val startedAt: LocalDateTime,
)
