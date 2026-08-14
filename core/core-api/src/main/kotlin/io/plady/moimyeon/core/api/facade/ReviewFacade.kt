package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.ReceivedReviewsResponse
import io.plady.moimyeon.core.api.controller.v1.response.ReviewTargetsResponse
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
