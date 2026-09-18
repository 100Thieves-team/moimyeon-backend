package io.plady.moimyeon.core.api.controller.v1.response

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ReviewTargetsResponseTest {
    @Test
    fun `deprecated 응답은 작성 후기가 있는 대상에만 후기 ID와 제출 상태를 표시한다`() {
        val submittedTargetId = UUID.randomUUID()
        val writableTargetId = UUID.randomUUID()
        val excludedTargetId = UUID.randomUUID()
        val overview = ReviewOverviewResponse(
            submittedCount = 1,
            totalCount = 2,
            targets = listOf(
                ReviewTargetResponse(submittedTargetId, "꼼꼼한 여우 12", ReviewTargetStatus.SUBMITTED),
                ReviewTargetResponse(writableTargetId, "성실한 사슴 03", ReviewTargetStatus.WRITABLE),
            ),
            reviews = listOf(
                writtenReview(reviewId = 31L, targetMemberId = submittedTargetId),
                writtenReview(reviewId = 32L, targetMemberId = excludedTargetId),
            ),
        )

        val result = ReviewTargetsResponse.from(overview)

        assertThat(result.submittedCount).isEqualTo(1)
        assertThat(result.totalCount).isEqualTo(2)
        assertThat(result.targets).containsExactly(
            LegacyReviewTargetResponse(
                memberId = submittedTargetId,
                nickname = "꼼꼼한 여우 12",
                status = ReviewTargetStatus.SUBMITTED,
                reviewId = 31L,
            ),
            LegacyReviewTargetResponse(
                memberId = writableTargetId,
                nickname = "성실한 사슴 03",
                status = ReviewTargetStatus.WRITABLE,
                reviewId = null,
            ),
        )
    }

    private fun writtenReview(reviewId: Long, targetMemberId: UUID): ReviewOverviewWrittenReviewResponse {
        return ReviewOverviewWrittenReviewResponse(
            reviewId = reviewId,
            targetMemberId = targetMemberId,
            tags = emptyList(),
            content = "",
            anonymous = true,
        )
    }
}
