package io.plady.moimyeon.security.auth

import java.time.Instant
import java.util.UUID

data class RestoreClaim(
    val memberId: UUID,
    val issuedAt: Instant,
)
