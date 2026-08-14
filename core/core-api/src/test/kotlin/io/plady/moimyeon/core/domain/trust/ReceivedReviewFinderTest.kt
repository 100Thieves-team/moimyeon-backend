package io.plady.moimyeon.core.domain.trust

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class ReceivedReviewFinderTest {
    private val reviewRepository = mockk<ReviewRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-08-14T03:00:00Z"), ZoneOffset.UTC)
    private val finder = ReceivedReviewFinder(reviewRepository, clock)
    private val memberId = UUID.randomUUID()

    @Test
    fun `페이지 크기보다 한 건 더 읽어 다음 받은 후기 존재 여부를 판단한다`() {
        val newest = review(3L, LocalDateTime.of(2026, 8, 14, 2, 59), "가장 최근 후기")
        val cursorRow = review(2L, LocalDateTime.of(2026, 8, 14, 2, 58), "현재 페이지 마지막 후기")
        val overflow = review(1L, LocalDateTime.of(2026, 8, 14, 2, 57), "다음 페이지 후기")
        every {
            reviewRepository.findVisibleReceivedReviewPage(
                memberId,
                LocalDateTime.of(2026, 8, 14, 3, 0),
                null,
                PageRequest.of(0, 3),
            )
        } returns listOf(newest, cursorRow, overflow)
        every { reviewRepository.findAllWithTagsByIdIn(listOf(3L, 2L)) } returns listOf(cursorRow, newest)
        every {
            reviewRepository.countVisibleReceivedReviews(memberId, LocalDateTime.of(2026, 8, 14, 3, 0))
        } returns 3L

        val page = finder.getPage(memberId, lastReviewId = null, size = 2)

        assertThat(page.reviews.map(ReceivedReview::id)).containsExactly(3L, 2L)
        assertThat(page.totalCount).isEqualTo(3L)
        assertThat(page.hasNext).isTrue()
    }

    @Test
    fun `받은 후기 페이지가 비어 있으면 태그 조회 없이 마지막 페이지를 반환한다`() {
        every {
            reviewRepository.findVisibleReceivedReviewPage(
                memberId,
                LocalDateTime.of(2026, 8, 14, 3, 0),
                null,
                PageRequest.of(0, 21),
            )
        } returns emptyList()
        every {
            reviewRepository.countVisibleReceivedReviews(memberId, LocalDateTime.of(2026, 8, 14, 3, 0))
        } returns 0L

        val page = finder.getPage(memberId, lastReviewId = null, size = 20)

        assertThat(page.reviews).isEmpty()
        assertThat(page.totalCount).isZero()
        assertThat(page.hasNext).isFalse()
        verify(exactly = 0) { reviewRepository.findAllWithTagsByIdIn(any()) }
    }

    private fun review(id: Long, visibleAt: LocalDateTime, content: String): ReviewEntity {
        return mockk<ReviewEntity>().also { review ->
            every { review.id } returns id
            every { review.visibleAt } returns visibleAt
            every { review.tags() } returns setOf("피드백이 구체적이에요")
            every { review.content } returns content
        }
    }
}
