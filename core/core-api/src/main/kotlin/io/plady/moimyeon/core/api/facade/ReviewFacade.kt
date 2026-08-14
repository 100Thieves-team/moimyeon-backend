package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.ReviewTargetsResponse
import io.plady.moimyeon.core.domain.member.MemberService
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
}
