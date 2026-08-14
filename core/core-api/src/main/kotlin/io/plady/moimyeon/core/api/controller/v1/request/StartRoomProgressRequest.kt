package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.progress.Attendance
import io.plady.moimyeon.core.enums.AttendanceStatus
import java.util.UUID

data class StartRoomProgressRequest(
    val roomId: UUID,
    val attendances: List<AttendanceRequest>,
) {
    fun toAttendances(): List<Attendance> = attendances.map(AttendanceRequest::toAttendance)
}

data class AttendanceRequest(
    val memberId: UUID,
    val status: AttendanceStatus,
) {
    fun toAttendance(): Attendance = Attendance(memberId, status)
}
