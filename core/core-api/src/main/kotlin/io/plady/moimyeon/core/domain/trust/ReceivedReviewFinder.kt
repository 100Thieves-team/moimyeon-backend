package io.plady.moimyeon.core.domain.trust

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class ReceivedReviewFinder(
    private val reviewRepository: ReviewRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun getPage(
        memberId: UUID,
        lastReviewId: Long?,
        size: Int,
    ): ReceivedReviewPage {
        log.debug { "received-review.finder.getPage memberId=$memberId lastReviewId=$lastReviewId size=$size" }
        val now = LocalDateTime.now(clock)
        val pageRows = reviewRepository.findVisibleReceivedReviewPage(
            memberId = memberId,
            now = now,
            lastReviewId = lastReviewId,
            pageable = PageRequest.of(0, size + 1),
        )
        val selectedRows = pageRows.take(size)
        val selectedIds = selectedRows.map { it.id }
        val reviewsById = if (selectedIds.isEmpty()) {
            emptyMap()
        } else {
            reviewRepository.findAllWithTagsByIdIn(selectedIds).associateBy { it.id }
        }
        val reviews = selectedIds.map { reviewId ->
            val review = reviewsById.getValue(reviewId)
            ReceivedReview(
                id = review.id,
                authorMemberId = review.authorMemberId,
                anonymous = review.anonymous,
                tags = review.tags(),
                content = review.content.orEmpty(),
            )
        }
        return ReceivedReviewPage(
            reviews = reviews,
            totalCount = reviewRepository.countVisibleReceivedReviews(memberId, now),
            hasNext = pageRows.size > size,
        )
    }
}
