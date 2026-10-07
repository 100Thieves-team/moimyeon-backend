package io.plady.moimyeon.core.domain.resume

import java.time.LocalDateTime
import java.util.UUID

data class Resume(
    val id: UUID,
    val name: String,
    val file: ResumeFile,
    val summary: ResumeSummary,
    val isDefault: Boolean,
    val registeredAt: LocalDateTime,
)
