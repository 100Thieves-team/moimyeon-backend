package io.plady.moimyeon.core.domain.trust

import java.util.UUID

data class ReviewSkipCommand(
    val roomId: UUID,
    val authorMemberId: UUID,
    val targetMemberId: UUID,
)
