package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ReviewEntity
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

        return review.toWrittenReview()
    }

    @Transactional(readOnly = true)
    fun getWrittenReviews(authorMemberId: UUID, roomId: UUID): List<WrittenReview> {
        return reviewRepository
            .findAllWithTagsByRoomIdAndAuthorMemberIdAndDeletedAtIsNull(roomId, authorMemberId)
            .map { it.toWrittenReview() }
    }

    private fun ReviewEntity.toWrittenReview(): WrittenReview = WrittenReview(
        id = id,
        roomId = roomId,
        targetMemberId = targetMemberId,
        tags = tags(),
        content = content.orEmpty(),
        anonymous = anonymous,
    )
}
