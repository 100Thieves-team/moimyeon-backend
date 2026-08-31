package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.progress.Attendance
import java.util.UUID

data class AttendanceResponse(
    val memberId: UUID,
    val nickname: String,
    val status: String,
) {
    companion object {
        fun from(attendance: Attendance, nicknames: Map<UUID, String>): AttendanceResponse = AttendanceResponse(
            memberId = attendance.memberId,
            nickname = nicknames[attendance.memberId] ?: WITHDRAWN_ATTENDEE_NICKNAME,
            status = attendance.status.name,
        )
    }
}

private const val WITHDRAWN_ATTENDEE_NICKNAME = "탈퇴한 회원"
