package io.plady.moimyeon.core.domain.progress

import io.plady.moimyeon.core.enums.AttendanceStatus
import java.util.UUID

data class Attendance(
    val memberId: UUID,
    val status: AttendanceStatus,
)
