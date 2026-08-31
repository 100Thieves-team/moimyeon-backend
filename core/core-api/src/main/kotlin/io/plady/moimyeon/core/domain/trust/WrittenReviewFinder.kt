package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class WrittenReviewFinder(
    private val reviewRepository: ReviewRepository,
) {
    @Transactional(readOnly = true)
    fun getWrittenReview(authorMemberId: UUID, reviewId: Long): WrittenReview {
        val review = requireFound(
            reviewRepository.findByIdAndDeletedAtIsNull(reviewId),
            CoreErrorType.REVIEW_NOT_FOUND,
        )
        requireBusiness(review.authorMemberId == authorMemberId, CoreErrorType.REVIEW_FORBIDDEN)

        return WrittenReview(
            id = review.id,
            roomId = review.roomId,
            targetMemberId = review.targetMemberId,
            tags = review.tags(),
            content = review.content,
            anonymous = review.anonymous,
        )
    }
}
