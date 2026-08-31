package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.progress.RoomProgressStartResult
import java.util.UUID

data class RoomProgressStartResponse(
    val status: String,
    val hostMemberId: UUID,
    val attendances: List<AttendanceResponse>,
) {
    companion object {
        fun from(result: RoomProgressStartResult, nicknames: Map<UUID, String>): RoomProgressStartResponse = RoomProgressStartResponse(
            status = result.status.name,
            hostMemberId = result.hostMemberId,
            attendances = result.attendances.map { AttendanceResponse.from(it, nicknames) },
        )
    }
}
