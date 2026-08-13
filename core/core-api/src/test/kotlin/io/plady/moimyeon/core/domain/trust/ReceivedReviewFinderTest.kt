package io.plady.moimyeon.core.domain.trust

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
    fun `공개 가능한 받은 후기를 익명 조회 모델로 변환한다`() {
        val review = mockk<ReviewEntity>()
        every { review.id } returns 1L
        every { review.tags() } returns setOf("피드백이 구체적이에요", "좋은 질문을 해요")
        every { review.content } returns "실전처럼 진행해주셨어요."
        every {
            reviewRepository.findVisibleReceivedReviews(memberId, LocalDateTime.of(2026, 8, 14, 3, 0))
        } returns listOf(review)

        val result = finder.getAll(memberId)

        assertThat(result).containsExactly(
            ReceivedReview(
                id = 1L,
                tags = setOf("피드백이 구체적이에요", "좋은 질문을 해요"),
                content = "실전처럼 진행해주셨어요.",
            ),
        )
        verify(exactly = 1) {
            reviewRepository.findVisibleReceivedReviews(memberId, LocalDateTime.of(2026, 8, 14, 3, 0))
        }
    }

    @Test
    fun `공개 가능한 후기가 없으면 빈 목록을 반환한다`() {
        every { reviewRepository.findVisibleReceivedReviews(memberId, any()) } returns emptyList()

        assertThat(finder.getAll(memberId)).isEmpty()
    }
}
