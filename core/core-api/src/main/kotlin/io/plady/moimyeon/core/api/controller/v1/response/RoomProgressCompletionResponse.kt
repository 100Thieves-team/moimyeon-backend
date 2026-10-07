package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.progress.RoomProgressCompletionResult
import java.util.UUID

data class RoomProgressCompletionResponse(
    val status: String,
    val attendances: List<AttendanceResponse>,
) {
    companion object {
        fun from(
            result: RoomProgressCompletionResult,
            nicknames: Map<UUID, String>,
        ): RoomProgressCompletionResponse = RoomProgressCompletionResponse(
            status = result.status.name,
            attendances = result.attendances.map { AttendanceResponse.from(it, nicknames) },
        )
    }
}
