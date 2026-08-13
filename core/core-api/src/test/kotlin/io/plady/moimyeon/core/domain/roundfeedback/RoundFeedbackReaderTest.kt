package io.plady.moimyeon.core.domain.roundfeedback

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.enums.RoundFeedbackType
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.QuestionCommentEntity
import io.plady.moimyeon.storage.db.core.QuestionCommentRepository
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import io.plady.moimyeon.storage.db.core.RoundFeedbackEntity
import io.plady.moimyeon.storage.db.core.RoundFeedbackRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoundFeedbackReaderTest {
    private val questionRepository = mockk<QuestionRepository>()
    private val commentRepository = mockk<QuestionCommentRepository>()
    private val feedbackRepository = mockk<RoundFeedbackRepository>()
    private val memberRepository = mockk<MemberRepository>()
    private val reader = RoundFeedbackReader(
        questionRepository,
        commentRepository,
        feedbackRepository,
        memberRepository,
    )
    private val roomId = UUID.randomUUID()
    private val intervieweeMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `질문 여부는 질문 저장소에서 결정하고 선택한 라운드의 질문에 내 댓글만 묶는다`() {
        val question = mockk<QuestionEntity>()
        val questionWithoutMyComment = mockk<QuestionEntity>()
        val comment = mockk<QuestionCommentEntity>()
        every { question.id } returns 11L
        every { question.content } returns "장애 원인을 어떻게 좁혔나요?"
        every { questionWithoutMyComment.id } returns 12L
        every { questionWithoutMyComment.content } returns "메모하지 않은 질문"
        every {
            questionRepository.findByRoomIdAndTargetMemberIdAndAskedTrueAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                intervieweeMemberId,
            )
        } returns listOf(question, questionWithoutMyComment)
        every {
            commentRepository.findAllByQuestionIdInAndAuthorMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                listOf(11L, 12L),
                authorMemberId,
            )
        } returns listOf(comment)
        every { comment.id } returns 21L
        every { comment.questionId } returns 11L
        every { comment.commentType } returns QuestionCommentType.GOOD_POINT
        every { comment.content } returns "원인을 단계적으로 좁힌 점이 좋아요"
        every { comment.createdAt } returns LocalDateTime.of(2026, 8, 14, 10, 0)

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
        val withdrawnMember = mockk<MemberEntity>()
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
        every { memberRepository.findAllById(listOf(authorMemberId)) } returns listOf(withdrawnMember)
        every { withdrawnMember.id } returns authorMemberId
        every { withdrawnMember.isDeleted() } returns true

        val result = reader.getIntervieweeFeedback(roomId, intervieweeMemberId)

        assertThat(result.selfFeedback).isNull()
        val card = result.finalFeedbacks.single()
        assertThat(card.author.displayName).isEqualTo("탈퇴 회원")
        assertThat(card.author.role).isEqualTo(RoundFeedbackAuthorRole.PARTICIPANT)
        assertThat(card.revealed).isFalse()
        assertThat(card.content).isNull()
    }
}
