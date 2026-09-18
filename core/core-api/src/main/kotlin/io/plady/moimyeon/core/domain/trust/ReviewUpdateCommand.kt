package io.plady.moimyeon.core.domain.trust

import java.util.UUID

data class ReviewUpdateCommand(
    val reviewId: Long,
    val authorMemberId: UUID,
    val tags: Set<String>,
    val content: String,
)
