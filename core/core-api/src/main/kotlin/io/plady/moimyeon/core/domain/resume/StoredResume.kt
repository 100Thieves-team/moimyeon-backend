package io.plady.moimyeon.core.domain.resume

import java.time.LocalDateTime
import java.util.UUID

data class StoredResume(
    val resume: Resume,
    val lastUsed: ResumeLastUsed?,
    val isDefault: Boolean,
)

data class ResumeLastUsed(
    val roomId: UUID,
    val roomTitle: String,
    val usedAt: LocalDateTime,
)
