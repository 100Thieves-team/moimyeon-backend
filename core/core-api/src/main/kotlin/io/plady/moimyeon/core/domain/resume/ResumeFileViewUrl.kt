package io.plady.moimyeon.core.domain.resume

import java.time.Instant

data class ResumeFileViewUrl(
    val url: String,
    val expiresAt: Instant,
)
