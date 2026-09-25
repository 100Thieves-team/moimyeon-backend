package io.plady.moimyeon.core.domain.progress

import java.time.LocalDateTime
import java.util.UUID

data class RoomAttendanceRecordCommand(
    val roomId: UUID,
    val recorderMemberId: UUID,
    val attendances: List<Attendance>,
    val recordedAt: LocalDateTime,
)
