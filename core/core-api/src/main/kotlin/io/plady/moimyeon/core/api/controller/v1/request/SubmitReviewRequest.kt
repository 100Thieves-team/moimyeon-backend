package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.trust.ReviewSubmissionContent
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.util.UUID

data class SubmitReviewRequest(
    val targetMemberId: UUID,
    val tags: Set<String> = emptySet(),
    val content: String? = null,
) {
    fun toContent(): ReviewSubmissionContent {
        return ReviewSubmissionContent(
            targetMemberId = targetMemberId,
            tags = normalizeReviewTags(tags),
            content = normalizeReviewContent(content),
        )
    }
}

internal fun normalizeReviewTags(tags: Set<String>): Set<String> {
    if (tags.any { it !in ReviewTagOption.labels }) {
        throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
    }
    return tags.toCollection(linkedSetOf())
}

internal fun normalizeReviewContent(content: String?): String? = content?.trim()?.takeIf(String::isNotEmpty)
