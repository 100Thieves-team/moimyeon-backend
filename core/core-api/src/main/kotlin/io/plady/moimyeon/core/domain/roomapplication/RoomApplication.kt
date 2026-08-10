package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.core.domain.resume.ResumeSummary
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import java.time.LocalDateTime
import java.util.UUID

data class RoomApplication(
    val id: Long,
    val roomId: UUID,
    val applicantMemberId: UUID,
    val note: String,
    val resumeSubmission: ResumeSubmission,
    val resumeSummary: ResumeSummary,
    val status: RoomApplicationStatus,
    val appliedAt: LocalDateTime,
)
