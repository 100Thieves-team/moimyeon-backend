package io.plady.moimyeon.core.api.facade

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.member.Nickname
import io.plady.moimyeon.core.domain.member.SocialAccount
import io.plady.moimyeon.core.domain.trust.ReceivedReview
import io.plady.moimyeon.core.domain.trust.ReceivedReviewPage
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
    private val namedReviewAuthorId = UUID.randomUUID()
    private val anonymousReviewAuthorId = UUID.randomUUID()

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

    @Test
    fun `받은 후기의 익명 작성자는 익명의 참여자로 표시하고 공개 작성자만 닉네임을 조회한다`() {
        val page = ReceivedReviewPage(
            reviews = listOf(
                receivedReview(id = 32L, authorMemberId = anonymousReviewAuthorId, anonymous = true),
                receivedReview(id = 31L, authorMemberId = namedReviewAuthorId, anonymous = false),
            ),
            totalCount = 2,
            hasNext = false,
        )
        every { reviewService.getReceivedReviewPage(authorMemberId, null, 20) } returns page
        every { memberService.getMembers(listOf(namedReviewAuthorId)) } returns listOf(
            member(namedReviewAuthorId, "꼼꼼한 여우 12"),
        )

        val result = facade.getReceivedReviews(authorMemberId, null, 20)

        assertThat(result.reviews.map { it.authorNickname }).containsExactly("익명의 참여자", "꼼꼼한 여우 12")
        verify(exactly = 1) { memberService.getMembers(listOf(namedReviewAuthorId)) }
        verify(exactly = 0) { memberService.getMembers(match { anonymousReviewAuthorId in it }) }
    }

    @Test
    fun `받은 후기가 모두 익명이면 회원을 조회하지 않는다`() {
        val page = ReceivedReviewPage(
            reviews = listOf(receivedReview(32L, anonymousReviewAuthorId, anonymous = true)),
            totalCount = 1,
            hasNext = false,
        )
        every { reviewService.getReceivedReviewPage(authorMemberId, null, 20) } returns page

        val result = facade.getReceivedReviews(authorMemberId, null, 20)

        assertThat(result.reviews.single().authorNickname).isEqualTo("익명의 참여자")
        verify(exactly = 0) { memberService.getMembers(any()) }
    }

    @Test
    fun `탈퇴한 공개 작성자는 탈퇴한 회원으로 표시한다`() {
        val page = ReceivedReviewPage(
            reviews = listOf(receivedReview(31L, namedReviewAuthorId, anonymous = false)),
            totalCount = 1,
            hasNext = false,
        )
        every { reviewService.getReceivedReviewPage(authorMemberId, 32L, 20) } returns page
        every { memberService.getMembers(listOf(namedReviewAuthorId)) } returns emptyList()

        val result = facade.getReceivedReviews(authorMemberId, 32L, 20)

        assertThat(result.reviews.single().authorNickname).isEqualTo("탈퇴한 회원")
    }

    private fun receivedReview(
        id: Long,
        authorMemberId: UUID,
        anonymous: Boolean,
    ): ReceivedReview {
        return ReceivedReview(
            id = id,
            authorMemberId = authorMemberId,
            anonymous = anonymous,
            tags = setOf("피드백이 구체적이에요"),
            content = "약점을 정확히 알았어요.",
        )
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
