package io.plady.moimyeon.core.api.facade

import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.member.Nickname
import io.plady.moimyeon.core.domain.member.SocialAccount
import io.plady.moimyeon.core.domain.trust.ReviewService
import io.plady.moimyeon.core.domain.trust.ReviewTarget
import io.plady.moimyeon.core.domain.trust.ReviewTargetStatus
import io.plady.moimyeon.core.enums.MemberRole
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ReviewFacadeTest {
    private val reviewService = mockk<ReviewService>()
    private val memberService = mockk<MemberService>()
    private val facade = ReviewFacade(reviewService, memberService)

    private val roomId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()
    private val submittedTargetId = UUID.randomUUID()
    private val withdrawnTargetId = UUID.randomUUID()

    @Test
    fun `후기 대상에 닉네임과 작성 진행 수를 조립한다`() {
        val targets = listOf(
            ReviewTarget(submittedTargetId, ReviewTargetStatus.SUBMITTED, reviewId = 31L),
            ReviewTarget(withdrawnTargetId, ReviewTargetStatus.WRITABLE),
        )
        every { reviewService.getTargets(authorMemberId, roomId) } returns targets
        every { memberService.getMembers(listOf(submittedTargetId, withdrawnTargetId)) } returns listOf(
            member(submittedTargetId, "꼼꼼한 여우 12"),
        )

        val result = facade.getTargets(authorMemberId, roomId)

        assertThat(result.submittedCount).isEqualTo(1)
        assertThat(result.totalCount).isEqualTo(2)
        assertThat(result.targets[0].reviewId).isEqualTo(31L)
        assertThat(result.targets[0].nickname).isEqualTo("꼼꼼한 여우 12")
        assertThat(result.targets[1].nickname).isEqualTo("탈퇴한 회원")
        verifyOrder {
            reviewService.getTargets(authorMemberId, roomId)
            memberService.getMembers(listOf(submittedTargetId, withdrawnTargetId))
        }
    }

    private fun member(id: UUID, nickname: String): Member {
        return Member(
            id = id,
            email = Email("$id@example.com"),
            nickname = Nickname(nickname),
            status = MemberStatus.ACTIVE,
            socialAccounts = listOf(SocialAccount(SocialLoginProvider.GOOGLE, id.toString(), null)),
            lastLoginAt = LocalDateTime.of(2026, 8, 14, 12, 0),
            role = MemberRole.USER,
        )
    }
}
