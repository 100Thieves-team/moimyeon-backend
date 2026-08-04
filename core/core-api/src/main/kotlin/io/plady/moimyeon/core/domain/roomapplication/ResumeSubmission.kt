package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.core.domain.resume.ResumeFile
import java.util.UUID

data class ResumeSubmission(
    val sourceResumeId: UUID,
    val file: ResumeFile,
)
