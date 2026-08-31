package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.ReceivedReviewsResponse
import io.plady.moimyeon.core.api.controller.v1.response.ReviewTargetsResponse
import io.plady.moimyeon.core.api.controller.v1.response.WrittenReviewResponse
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.trust.ReceivedReview
import io.plady.moimyeon.core.domain.trust.ReviewService
import io.plady.moimyeon.core.domain.trust.ReviewTarget
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ReviewFacade(
    private val reviewService: ReviewService,
    private val memberService: MemberService,
) {
    fun getTargets(authorMemberId: UUID, roomId: UUID): ReviewTargetsResponse {
        val targets = reviewService.getTargets(authorMemberId, roomId)
        val nicknames = memberService.getMembers(targets.map(ReviewTarget::memberId))
            .associate { it.id to it.nickname.value }
        return ReviewTargetsResponse.from(targets, nicknames)
    }

    // 수정 화면 헤더가 "누구에게 쓴 후기"인지 그릴 수 있게 대상자 닉네임을 붙인다(MOI-496).
    fun getWrittenReview(authorMemberId: UUID, reviewId: Long): WrittenReviewResponse {
        val review = reviewService.getWrittenReview(authorMemberId, reviewId)
        val nicknames = memberService.getMembers(listOf(review.targetMemberId))
            .associate { it.id to it.nickname.value }
        return WrittenReviewResponse.from(review, nicknames)
    }

    fun getReceivedReviews(
        memberId: UUID,
        lastReviewId: Long?,
        size: Int,
    ): ReceivedReviewsResponse {
        val page = reviewService.getReceivedReviewPage(memberId, lastReviewId, size)
        val publicAuthorIds = page.reviews
            .filterNot(ReceivedReview::anonymous)
            .map(ReceivedReview::authorMemberId)
            .distinct()
        val authorNicknames = if (publicAuthorIds.isEmpty()) {
            emptyMap()
        } else {
            memberService.getMembers(publicAuthorIds)
                .associate { it.id to it.nickname.value }
        }
        return ReceivedReviewsResponse.from(page, authorNicknames)
    }
}
