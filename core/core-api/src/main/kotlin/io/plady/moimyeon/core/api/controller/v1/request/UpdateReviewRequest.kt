package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.trust.ReviewUpdateContent

data class UpdateReviewRequest(
    val tags: Set<String> = emptySet(),
    val content: String? = null,
) {
    fun toContent(): ReviewUpdateContent {
        return ReviewUpdateContent(
            tags = normalizeReviewTags(tags),
            content = normalizeReviewContent(content),
        )
    }
}
