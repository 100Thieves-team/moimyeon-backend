package io.plady.moimyeon.core.domain.trust

import java.util.UUID

data class ReceivedReview(
    val id: Long,
    val authorMemberId: UUID,
    val anonymous: Boolean,
    val tags: Set<String>,
    val content: String,
)
