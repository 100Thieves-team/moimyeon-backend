package io.plady.moimyeon.core.domain.progress

import io.plady.moimyeon.core.enums.RoomStatus
import java.util.UUID

data class RoomProgressStartResult(
    val status: RoomStatus,
    val hostMemberId: UUID,
    val attendances: List<Attendance>,
)
