package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.trust.ReceivedReview
import io.plady.moimyeon.core.domain.trust.ReceivedReviewPage
import java.util.UUID

data class ReceivedReviewsResponse(
    val totalCount: Long,
    val reviews: List<ReceivedReviewResponse>,
    val hasNext: Boolean,
) {
    companion object {
        fun from(page: ReceivedReviewPage, authorNicknames: Map<UUID, String>): ReceivedReviewsResponse {
            return ReceivedReviewsResponse(
                totalCount = page.totalCount,
                reviews = page.reviews.map { ReceivedReviewResponse.from(it, authorNicknames) },
                hasNext = page.hasNext,
            )
        }
    }
}

data class ReceivedReviewResponse(
    val reviewId: Long,
    val authorNickname: String,
    val tags: List<String>,
    val content: String,
) {
    companion object {
        fun from(review: ReceivedReview, authorNicknames: Map<UUID, String>): ReceivedReviewResponse {
            return ReceivedReviewResponse(
                reviewId = review.id,
                authorNickname = if (review.anonymous) {
                    ANONYMOUS_REVIEW_AUTHOR_NICKNAME
                } else {
                    authorNicknames[review.authorMemberId] ?: WITHDRAWN_REVIEW_AUTHOR_NICKNAME
                },
                tags = review.tags.sorted(),
                content = review.content,
            )
        }
    }
}

private const val ANONYMOUS_REVIEW_AUTHOR_NICKNAME = "익명의 참여자"
private const val WITHDRAWN_REVIEW_AUTHOR_NICKNAME = "탈퇴한 회원"
