package io.plady.moimyeon.core.domain.room

import java.time.LocalDateTime

data class ResumeOriginalViewUrl(
    val url: String,
    val expiresAt: LocalDateTime,
)
