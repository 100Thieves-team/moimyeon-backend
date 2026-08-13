package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class ReceivedReviewFinder(
    private val reviewRepository: ReviewRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun getAll(memberId: UUID): List<ReceivedReview> {
        return reviewRepository.findVisibleReceivedReviews(memberId, LocalDateTime.now(clock))
            .map { review ->
                ReceivedReview(
                    id = review.id,
                    tags = review.tags(),
                    content = review.content,
                )
            }
    }
}
