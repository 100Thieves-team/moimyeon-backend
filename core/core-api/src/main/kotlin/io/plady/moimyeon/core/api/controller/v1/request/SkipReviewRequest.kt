package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.trust.ReviewSkipContent
import java.util.UUID

data class SkipReviewRequest(
    val targetMemberId: UUID,
) {
    fun toContent(): ReviewSkipContent {
        return ReviewSkipContent(
            targetMemberId = targetMemberId,
        )
    }
}
