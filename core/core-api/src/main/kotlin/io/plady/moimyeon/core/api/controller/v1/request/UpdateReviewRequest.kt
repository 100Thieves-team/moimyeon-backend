package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.trust.ReviewUpdateContent
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

data class UpdateReviewRequest(
    val tags: Set<String> = emptySet(),
    val content: String? = null,
) {
    fun toContent(): ReviewUpdateContent {
        if (tags.any { it !in ReviewTagOption.labels }) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }

        return ReviewUpdateContent(
            tags = tags.toCollection(linkedSetOf()),
            content = content?.trim()?.takeIf(String::isNotEmpty),
        )
    }
}
