package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.core.enums.RoomApplicationStatus
import java.time.LocalDateTime

data class RoomApplicationDetails(
    val applicationId: Long,
    val applicant: ApplicationApplicant,
    val note: String,
    val resumeSummary: ApplicationResumeSummary,
    val status: RoomApplicationStatus,
    val appliedAt: LocalDateTime,
)
