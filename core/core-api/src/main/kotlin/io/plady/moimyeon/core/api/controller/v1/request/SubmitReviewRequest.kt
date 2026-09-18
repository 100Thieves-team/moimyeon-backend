package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.trust.ReviewSubmissionContent
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.util.UUID

data class SubmitReviewRequest(
    val targetMemberId: UUID,
    val tags: Set<String> = emptySet(),
    val content: String? = null,
    val anonymous: Boolean = true,
) {
    fun toContent(): ReviewSubmissionContent {
        if (tags.any { it !in ReviewTagOption.labels }) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }

        return ReviewSubmissionContent(
            targetMemberId = targetMemberId,
            tags = tags.toCollection(linkedSetOf()),
            content = content?.trim().orEmpty(),
            anonymous = anonymous,
        )
    }
}
