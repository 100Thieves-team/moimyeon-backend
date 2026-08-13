package io.plady.moimyeon.core.domain.roundfeedback

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.domain.member.MemberAttribution
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.question.QuestionMemoComment
import io.plady.moimyeon.core.domain.question.QuestionMemoRecord
import io.plady.moimyeon.core.domain.question.QuestionMemoRecordReader
import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.enums.RoundFeedbackType
import io.plady.moimyeon.storage.db.core.RoundFeedbackEntity
import io.plady.moimyeon.storage.db.core.RoundFeedbackRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoundFeedbackReaderTest {
    private val questionMemoRecordReader = mockk<QuestionMemoRecordReader>()
    private val memberFinder = mockk<MemberFinder>()
    private val feedbackRepository = mockk<RoundFeedbackRepository>()
    private val reader = RoundFeedbackReader(
        questionMemoRecordReader,
        memberFinder,
        feedbackRepository,
    )
    private val roomId = UUID.randomUUID()
    private val intervieweeMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `질문 개념이 선별한 내 메모 기록을 라운드 피드백 기록으로 변환한다`() {
        every {
            questionMemoRecordReader.getAskedRecordsByAuthor(
                roomId,
                intervieweeMemberId,
                authorMemberId,
            )
        } returns listOf(
            QuestionMemoRecord(
                questionId = 11L,
                questionContent = "장애 원인을 어떻게 좁혔나요?",
                comments = listOf(
                    QuestionMemoComment(
                        id = 21L,
                        type = QuestionCommentType.GOOD_POINT,
                        content = "원인을 단계적으로 좁힌 점이 좋아요",
                        createdAt = LocalDateTime.of(2026, 8, 14, 10, 0),
                    ),
                ),
            ),
        )

        val result = reader.getMyQuestionRecords(roomId, intervieweeMemberId, authorMemberId)

        assertThat(result).containsExactly(
            RoundQuestionRecord(
                questionId = 11L,
                questionContent = "장애 원인을 어떻게 좁혔나요?",
                comments = listOf(
                    RoundQuestionComment(
                        id = 21L,
                        type = QuestionCommentType.GOOD_POINT,
                        content = "원인을 단계적으로 좁힌 점이 좋아요",
                        createdAt = LocalDateTime.of(2026, 8, 14, 10, 0),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `탈퇴한 작성자의 최종 피드백은 탈퇴 회원 참여자로 표시하고 열람 전 본문은 가린다`() {
        val finalFeedback = mockk<RoundFeedbackEntity>()
        every {
            feedbackRepository.findAllByRoomIdAndIntervieweeMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                intervieweeMemberId,
            )
        } returns listOf(finalFeedback)
        every { finalFeedback.id } returns 31L
        every { finalFeedback.authorMemberId } returns authorMemberId
        every { finalFeedback.feedbackType } returns RoundFeedbackType.FINAL
        every { finalFeedback.content } returns "가려져야 할 피드백"
        every { finalFeedback.disclosedAt } returns null
        every {
            memberFinder.getAttributionsIncludingWithdrawn(listOf(authorMemberId))
        } returns listOf(
            MemberAttribution(
                id = authorMemberId,
                nickname = "이전 닉네임",
                withdrawn = true,
            ),
        )

        val result = reader.getIntervieweeFeedback(roomId, intervieweeMemberId)

        assertThat(result.selfFeedback).isNull()
        val card = result.finalFeedbacks.single()
        assertThat(card.author.displayName).isEqualTo("탈퇴 회원")
        assertThat(card.author.role).isEqualTo(RoundFeedbackAuthorRole.PARTICIPANT)
        assertThat(card.revealed).isFalse()
        assertThat(card.content).isNull()
    }
}
