package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.progress.Attendance
import java.util.UUID

data class AttendanceResponse(
    val memberId: UUID,
    val status: String,
) {
    companion object {
        fun from(attendance: Attendance): AttendanceResponse = AttendanceResponse(
            memberId = attendance.memberId,
            status = attendance.status.name,
        )
    }
}
