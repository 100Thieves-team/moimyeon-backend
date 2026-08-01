package io.plady.moimyeon.core.domain.session

import java.time.LocalDateTime

data class Session(
    val credential: SessionCredential,
    val expiresAt: LocalDateTime,
)
