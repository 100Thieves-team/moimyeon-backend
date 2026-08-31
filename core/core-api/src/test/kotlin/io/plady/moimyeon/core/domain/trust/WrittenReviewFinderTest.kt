package io.plady.moimyeon.core.domain.trust

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class WrittenReviewFinderTest {
    private val reviewRepository = mockk<ReviewRepository>()
    private val finder = WrittenReviewFinder(reviewRepository)
    private val roomId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val review = ReviewEntity(
        roomId = roomId,
        authorMemberId = authorMemberId,
        targetMemberId = targetMemberId,
        content = "꼬리질문이 날카로워서 실전 같았어요.",
        visibleAt = LocalDateTime.of(2026, 8, 14, 6, 0),
        anonymous = false,
        tags = setOf("시간을 잘 지켜요", "피드백이 구체적이에요"),
    )

    @Test
    fun `작성자는 자신이 작성한 후기의 태그와 텍스트, 익명 여부를 조회한다`() {
        every { reviewRepository.findByIdAndDeletedAtIsNull(1L) } returns review

        val result = finder.getWrittenReview(authorMemberId, 1L)

        assertThat(result).isEqualTo(
            WrittenReview(
                id = review.id,
                roomId = roomId,
                targetMemberId = targetMemberId,
                tags = setOf("시간을 잘 지켜요", "피드백이 구체적이에요"),
                content = "꼬리질문이 날카로워서 실전 같았어요.",
                anonymous = false,
            ),
        )
    }

    @Test
    fun `태그와 텍스트가 없는 빈 후기도 그대로 조회한다`() {
        val emptyReview = ReviewEntity(
            roomId = roomId,
            authorMemberId = authorMemberId,
            targetMemberId = targetMemberId,
            visibleAt = LocalDateTime.of(2026, 8, 14, 6, 0),
            anonymous = true,
        )
        every { reviewRepository.findByIdAndDeletedAtIsNull(1L) } returns emptyReview

        val result = finder.getWrittenReview(authorMemberId, 1L)

        assertThat(result.tags).isEmpty()
        assertThat(result.content).isNull()
        assertThat(result.anonymous).isTrue()
    }

    @Test
    fun `공개 기준 시각이 지난 후기도 작성자는 조회한다`() {
        val visibleReview = ReviewEntity(
            roomId = roomId,
            authorMemberId = authorMemberId,
            targetMemberId = targetMemberId,
            content = "공개된 후기",
            visibleAt = LocalDateTime.of(2020, 1, 1, 0, 0),
            anonymous = true,
        )
        every { reviewRepository.findByIdAndDeletedAtIsNull(1L) } returns visibleReview

        val result = finder.getWrittenReview(authorMemberId, 1L)

        assertThat(result.content).isEqualTo("공개된 후기")
    }

    @Test
    fun `숨김·신고 상태의 후기도 작성자는 조회한다`() {
        val hiddenReview = ReviewEntity(
            roomId = roomId,
            authorMemberId = authorMemberId,
            targetMemberId = targetMemberId,
            content = "숨김 처리된 후기",
            visibleAt = LocalDateTime.of(2026, 8, 14, 6, 0),
            anonymous = true,
            hiddenAt = LocalDateTime.of(2026, 8, 15, 0, 0),
            reportedAt = LocalDateTime.of(2026, 8, 14, 23, 0),
        )
        every { reviewRepository.findByIdAndDeletedAtIsNull(1L) } returns hiddenReview

        val result = finder.getWrittenReview(authorMemberId, 1L)

        assertThat(result.content).isEqualTo("숨김 처리된 후기")
    }

    @Test
    fun `활성 후기가 없으면 E2006 을 던진다`() {
        every { reviewRepository.findByIdAndDeletedAtIsNull(1L) } returns null

        assertThatThrownBy { finder.getWrittenReview(authorMemberId, 1L) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.REVIEW_NOT_FOUND)
            }
    }

    @Test
    fun `작성자가 아니면 E2007 을 던진다`() {
        every { reviewRepository.findByIdAndDeletedAtIsNull(1L) } returns review

        assertThatThrownBy { finder.getWrittenReview(UUID.randomUUID(), 1L) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.REVIEW_FORBIDDEN)
            }
    }
}
