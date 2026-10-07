package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.domain.resume.ResumeSummary
import java.util.UUID

data class RoomParticipantResume(
    val roomId: UUID,
    val participantMemberId: UUID,
    val submissionId: Long,
    val sourceResumeId: UUID,
    val summary: ResumeSummary,
)
