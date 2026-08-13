package io.plady.moimyeon.core.api.facade

import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.member.Nickname
import io.plady.moimyeon.core.domain.member.SocialAccount
import io.plady.moimyeon.core.domain.question.FollowUpQuestion
import io.plady.moimyeon.core.domain.question.QuestionCard
import io.plady.moimyeon.core.domain.question.QuestionCardSet
import io.plady.moimyeon.core.domain.question.QuestionCardSetService
import io.plady.moimyeon.core.domain.question.QuestionResumeReference
import io.plady.moimyeon.core.domain.question.QuestionResumeReferenceService
import io.plady.moimyeon.core.domain.question.QuestionResumeSummary
import io.plady.moimyeon.core.enums.MemberRole
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class QuestionPreparationFacadeTest {
    private val cardSetService = mockk<QuestionCardSetService>()
    private val resumeReferenceService = mockk<QuestionResumeReferenceService>()
    private val memberService = mockk<MemberService>()
    private val facade = QuestionPreparationFacade(cardSetService, resumeReferenceService, memberService)

    private val roomId = UUID.randomUUID()
    private val viewerMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `카드셋 목록은 대상 닉네임과 질문 수를 붙이고 본인 준비 인원 수를 분리한다`() {
        val cardSets = listOf(cardSet())
        every { cardSetService.getCardSets(viewerMemberId, roomId) } returns cardSets
        every { cardSetService.getMyCardSetPreparerCount(viewerMemberId, roomId) } returns 2
        every { memberService.getMembers(listOf(targetMemberId)) } returns listOf(member(targetMemberId, "성실한 사슴 03"))

        val result = facade.getCardSets(viewerMemberId, roomId)

        assertThat(result.myCardSetPreparerCount).isEqualTo(2)
        assertThat(result.cardSets.single().target.nickname).isEqualTo("성실한 사슴 03")
        assertThat(result.cardSets.single().questionCount).isEqualTo(1)
        assertThat(result.cardSets.single().followUpQuestionCount).isEqualTo(1)
        verifyOrder {
            cardSetService.getCardSets(viewerMemberId, roomId)
            cardSetService.getMyCardSetPreparerCount(viewerMemberId, roomId)
            memberService.getMembers(listOf(targetMemberId))
        }
    }

    @Test
    fun `선택한 카드셋은 AI 요약과 질문 작성자 닉네임을 한 화면 응답으로 조립한다`() {
        val cardSet = cardSet()
        val reference = QuestionResumeReference(
            targetMemberId = targetMemberId,
            summary = QuestionResumeSummary.Done("결제 연동과 배치 처리 경험이 있어요."),
        )
        every { cardSetService.getCardSet(viewerMemberId, roomId, targetMemberId) } returns cardSet
        every {
            resumeReferenceService.getResumeReference(viewerMemberId, roomId, targetMemberId)
        } returns reference
        every {
            memberService.getMembers(listOf(targetMemberId, authorMemberId))
        } returns listOf(
            member(targetMemberId, "성실한 사슴 03"),
            member(authorMemberId, "든든한 곰 21"),
        )

        val result = facade.getCardSet(viewerMemberId, roomId, targetMemberId)

        assertThat(result.target.nickname).isEqualTo("성실한 사슴 03")
        assertThat(result.resumeSummary.status).isEqualTo("DONE")
        assertThat(result.resumeSummary.text).isEqualTo("결제 연동과 배치 처리 경험이 있어요.")
        assertThat(result.questions.single().author.nickname).isEqualTo("든든한 곰 21")
        assertThat(result.questions.single().source).isEqualTo("PREPARATION")
        assertThat(result.questions.single().followUps.single().author.nickname).isEqualTo("든든한 곰 21")
    }

    private fun cardSet(): QuestionCardSet {
        return QuestionCardSet(
            targetMemberId = targetMemberId,
            questions = listOf(
                QuestionCard(
                    id = 1L,
                    authorMemberId = authorMemberId,
                    content = "결제 연동에서 이중 결제를 어떻게 막았나요?",
                    source = QuestionSource.PREPARATION,
                    asked = false,
                    followUps = listOf(
                        FollowUpQuestion(
                            id = 2L,
                            authorMemberId = authorMemberId,
                            content = "멱등 키는 어디에 저장했나요?",
                            source = QuestionSource.PREPARATION,
                            asked = false,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun member(id: UUID, nickname: String): Member {
        return Member(
            id = id,
            email = Email("$id@example.com"),
            nickname = Nickname(nickname),
            status = MemberStatus.ACTIVE,
            role = MemberRole.USER,
            socialAccounts = listOf(SocialAccount(SocialLoginProvider.GOOGLE, id.toString(), null)),
            lastLoginAt = LocalDateTime.of(2026, 8, 13, 12, 0),
        )
    }
}
