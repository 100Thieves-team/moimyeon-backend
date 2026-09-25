package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.progress.RoomAttendanceRecordResult
import java.util.UUID

data class RoomAttendancesResponse(
    val attendances: List<AttendanceResponse>,
) {
    companion object {
        fun from(
            result: RoomAttendanceRecordResult,
            nicknames: Map<UUID, String>,
        ): RoomAttendancesResponse = RoomAttendancesResponse(
            attendances = result.attendances.map { AttendanceResponse.from(it, nicknames) },
        )
    }
}
