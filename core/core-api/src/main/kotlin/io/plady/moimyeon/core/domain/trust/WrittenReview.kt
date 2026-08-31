package io.plady.moimyeon.core.domain.trust

import java.util.UUID

data class WrittenReview(
    val id: Long,
    val roomId: UUID,
    val targetMemberId: UUID,
    val tags: Set<String>,
    val content: String?,
    val anonymous: Boolean,
)
