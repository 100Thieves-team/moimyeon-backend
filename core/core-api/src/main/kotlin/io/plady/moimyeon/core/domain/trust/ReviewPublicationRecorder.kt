package io.plady.moimyeon.core.domain.trust

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.event.OutboxEventPublisher
import io.plady.moimyeon.core.event.payload.ReviewPublishedEventPayload
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

// 후기는 작성 3시간 뒤 공개되므로 공개 시각이 지난 뒤 발행한다.
// 여러 서버가 같은 후기를 골라도 markNotified 는 한 곳만 성공하므로 분산 락이 필요 없다.
@Component
class ReviewPublicationRecorder(
    private val reviewRepository: ReviewRepository,
    private val roomFinder: RoomFinder,
    private val outboxEventPublisher: OutboxEventPublisher,
) {
    @Transactional
    fun recordPublishedReviews(now: LocalDateTime): Int {
        val candidates = reviewRepository.findNotificationCandidates(now, PageRequest.of(0, BATCH_SIZE))
        if (candidates.isEmpty()) return 0

        val roomTitles = roomFinder.getAllByIds(candidates.map { it.roomId }.toSet())
            .associate { it.id to it.title.value }
        var published = 0
        candidates.forEach { candidate ->
            if (reviewRepository.markNotified(candidate.reviewId, now) == 0) return@forEach
            // 발행하지 않는 후기도 기록해야 다시 고르지 않는다.
            if (candidate.hiddenAt != null) return@forEach
            val roomTitle = roomTitles[candidate.roomId] ?: run {
                log.warn { "review.publication.room-missing reviewId=${candidate.reviewId} roomId=${candidate.roomId}" }
                return@forEach
            }
            outboxEventPublisher.publish(
                EventType.REVIEW_PUBLISHED,
                ReviewPublishedEventPayload(
                    reviewId = candidate.reviewId,
                    roomId = candidate.roomId,
                    roomTitle = roomTitle,
                    authorMemberId = candidate.authorMemberId,
                    targetMemberId = candidate.targetMemberId,
                ),
            )
            published++
        }
        log.debug { "review.publication.recorded candidates=${candidates.size} published=$published" }
        return published
    }

    private companion object {
        const val BATCH_SIZE = 100
    }
}
