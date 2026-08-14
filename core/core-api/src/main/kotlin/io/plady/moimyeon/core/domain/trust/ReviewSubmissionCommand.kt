package io.plady.moimyeon.core.domain.trust

import java.util.UUID

data class ReviewSubmissionCommand(
    val roomId: UUID,
    val authorMemberId: UUID,
    val targetMemberId: UUID,
    val tags: Set<String>,
    val content: String?,
    val anonymous: Boolean,
)
