package io.plady.moimyeon.core.domain.trust

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class ReviewEditorTest {
    private val reviewRepository = mockk<ReviewRepository>()
    private val now = LocalDateTime.of(2026, 8, 14, 3, 0)
    private val clock = Clock.fixed(Instant.parse("2026-08-14T03:00:00Z"), ZoneOffset.UTC)
    private val editor = ReviewEditor(reviewRepository, clock)
    private val authorMemberId = UUID.randomUUID()
    private val otherMemberId = UUID.randomUUID()
    private val review = ReviewEntity(
        roomId = UUID.randomUUID(),
        authorMemberId = authorMemberId,
        targetMemberId = UUID.randomUUID(),
        content = "수정 전 후기",
        visibleAt = now.plusMinutes(5),
        anonymous = true,
        tags = setOf("시간을 잘 지켜요"),
    )

    @BeforeEach
    fun setUp() {
        every { reviewRepository.findForUpdateByIdAndDeletedAtIsNull(1L) } returns review
    }

    @Test
    fun `공개 기준 시각 전에는 작성자가 태그와 텍스트를 수정한다`() {
        val command = ReviewUpdateCommand(
            reviewId = 1L,
            authorMemberId = authorMemberId,
            tags = setOf("피드백이 구체적이에요", "좋은 질문을 해요"),
            content = "수정한 후기",
        )

        editor.update(command)

        assertThat(review.tags()).containsExactlyInAnyOrderElementsOf(command.tags)
        assertThat(review.content).isEqualTo(command.content)
        verify(exactly = 1) { reviewRepository.findForUpdateByIdAndDeletedAtIsNull(1L) }
    }

    @Test
    fun `태그와 텍스트를 모두 비워 수정할 수 있다`() {
        editor.update(
            ReviewUpdateCommand(
                reviewId = 1L,
                authorMemberId = authorMemberId,
                tags = emptySet(),
                content = null,
            ),
        )

        assertThat(review.tags()).isEmpty()
        assertThat(review.content).isNull()
    }

    @Test
    fun `공개 기준 시각 전에는 작성자가 후기를 소프트 삭제한다`() {
        editor.delete(authorMemberId, 1L)

        assertThat(review.isDeleted()).isTrue()
        verify(exactly = 1) { reviewRepository.findForUpdateByIdAndDeletedAtIsNull(1L) }
    }

    @Test
    fun `활성 후기가 없으면 E2006 을 던진다`() {
        every { reviewRepository.findForUpdateByIdAndDeletedAtIsNull(1L) } returns null

        assertUpdateFails(authorMemberId, CoreErrorType.REVIEW_NOT_FOUND)
    }

    @Test
    fun `작성자가 아니면 E2007 을 던지고 후기를 변경하지 않는다`() {
        assertUpdateFails(otherMemberId, CoreErrorType.REVIEW_FORBIDDEN)

        assertThat(review.tags()).containsExactly("시간을 잘 지켜요")
        assertThat(review.content).isEqualTo("수정 전 후기")
        assertThat(review.isDeleted()).isFalse()
    }

    @Test
    fun `공개 기준 시각이 지나면 E2008 을 던지고 후기를 변경하지 않는다`() {
        val expiredReview = ReviewEntity(
            roomId = UUID.randomUUID(),
            authorMemberId = authorMemberId,
            targetMemberId = UUID.randomUUID(),
            content = "공개된 후기",
            visibleAt = now.minusMinutes(1),
            anonymous = true,
            tags = setOf("시간을 잘 지켜요"),
        )
        every { reviewRepository.findForUpdateByIdAndDeletedAtIsNull(1L) } returns expiredReview

        assertUpdateFails(authorMemberId, CoreErrorType.REVIEW_EDIT_WINDOW_CLOSED)

        assertThat(expiredReview.content).isEqualTo("공개된 후기")
        assertThat(expiredReview.isDeleted()).isFalse()
    }

    @Test
    fun `작성자가 아니면 공개 기준 시각 전에도 후기를 삭제하지 않는다`() {
        assertDeleteFails(otherMemberId, CoreErrorType.REVIEW_FORBIDDEN)

        assertThat(review.isDeleted()).isFalse()
    }

    @Test
    fun `공개 기준 시각이 지나면 후기를 삭제하지 않는다`() {
        val expiredReview = ReviewEntity(
            roomId = UUID.randomUUID(),
            authorMemberId = authorMemberId,
            targetMemberId = UUID.randomUUID(),
            visibleAt = now.minusMinutes(1),
            anonymous = true,
        )
        every { reviewRepository.findForUpdateByIdAndDeletedAtIsNull(1L) } returns expiredReview

        assertDeleteFails(authorMemberId, CoreErrorType.REVIEW_EDIT_WINDOW_CLOSED)

        assertThat(expiredReview.isDeleted()).isFalse()
    }

    private fun assertUpdateFails(memberId: UUID, errorType: CoreErrorType) {
        val command = ReviewUpdateCommand(
            reviewId = 1L,
            authorMemberId = memberId,
            tags = setOf("수정 태그"),
            content = "수정 후기",
        )

        assertThatThrownBy { editor.update(command) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }

    private fun assertDeleteFails(memberId: UUID, errorType: CoreErrorType) {
        assertThatThrownBy { editor.delete(memberId, 1L) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
