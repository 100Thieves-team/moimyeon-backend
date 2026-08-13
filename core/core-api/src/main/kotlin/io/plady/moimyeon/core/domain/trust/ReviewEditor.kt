package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class ReviewEditor(
    private val reviewRepository: ReviewRepository,
    private val clock: Clock,
) {
    @Transactional
    fun update(command: ReviewUpdateCommand) {
        val review = getEditableReview(
            reviewId = command.reviewId,
            authorMemberId = command.authorMemberId,
            now = LocalDateTime.now(clock),
        )
        review.update(command.tags, command.content)
    }

    @Transactional
    fun delete(authorMemberId: UUID, reviewId: Long) {
        val now = LocalDateTime.now(clock)
        val review = getEditableReview(reviewId, authorMemberId, now)
        review.delete(now)
    }

    private fun getEditableReview(
        reviewId: Long,
        authorMemberId: UUID,
        now: LocalDateTime,
    ): ReviewEntity {
        val review = requireFound(
            reviewRepository.findForUpdateByIdAndDeletedAtIsNull(reviewId),
            CoreErrorType.REVIEW_NOT_FOUND,
        )
        requireBusiness(review.authorMemberId == authorMemberId, CoreErrorType.REVIEW_FORBIDDEN)
        requireBusiness(now.isBefore(review.visibleAt), CoreErrorType.REVIEW_EDIT_WINDOW_CLOSED)
        return review
    }
}
