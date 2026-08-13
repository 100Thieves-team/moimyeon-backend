package io.plady.moimyeon.core.domain.roundfeedback

import io.plady.moimyeon.core.enums.RoundFeedbackType
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.QuestionCommentRepository
import io.plady.moimyeon.storage.db.core.QuestionRepository
import io.plady.moimyeon.storage.db.core.RoundFeedbackEntity
import io.plady.moimyeon.storage.db.core.RoundFeedbackRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class RoundFeedbackReader(
    private val questionRepository: QuestionRepository,
    private val questionCommentRepository: QuestionCommentRepository,
    private val feedbackRepository: RoundFeedbackRepository,
    private val memberRepository: MemberRepository,
) {
    @Transactional(readOnly = true)
    fun getMyQuestionRecords(
        roomId: UUID,
        intervieweeMemberId: UUID,
        authorMemberId: UUID,
    ): List<RoundQuestionRecord> {
        val questions = questionRepository
            .findByRoomIdAndTargetMemberIdAndAskedTrueAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                intervieweeMemberId,
            )
        if (questions.isEmpty()) return emptyList()

        val commentsByQuestionId = questionCommentRepository
            .findAllByQuestionIdInAndAuthorMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                questions.map { it.id },
                authorMemberId,
            )
            .groupBy { it.questionId }
        return questions.mapNotNull { question ->
            val comments = commentsByQuestionId[question.id].orEmpty()
            if (comments.isEmpty()) return@mapNotNull null
            RoundQuestionRecord(
                questionId = question.id,
                questionContent = question.content,
                comments = comments.map { comment ->
                    RoundQuestionComment(
                        id = comment.id,
                        type = comment.commentType,
                        content = comment.content,
                        createdAt = comment.createdAt,
                    )
                },
            )
        }
    }

    @Transactional(readOnly = true)
    fun getIntervieweeFeedback(
        roomId: UUID,
        intervieweeMemberId: UUID,
    ): IntervieweeRoundFeedback {
        val feedbacks = feedbackRepository
            .findAllByRoomIdAndIntervieweeMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                intervieweeMemberId,
            )
        val finalFeedbacks = feedbacks.filter { it.feedbackType == RoundFeedbackType.FINAL }
        val authorsById = memberRepository.findAllById(finalFeedbacks.map { it.authorMemberId })
            .associateBy { it.id }
        return IntervieweeRoundFeedback(
            selfFeedback = feedbacks.firstOrNull { it.feedbackType == RoundFeedbackType.SELF }
                ?.let { SelfFeedback(it.id, it.content) },
            finalFeedbacks = finalFeedbacks.map { it.toCard(authorsById[it.authorMemberId]) },
        )
    }

    private fun RoundFeedbackEntity.toCard(author: MemberEntity?): FinalFeedbackCard {
        val revealed = disclosedAt != null
        return FinalFeedbackCard(
            id = id,
            author = RoundFeedbackAuthor(
                memberId = authorMemberId,
                displayName = author?.takeUnless { it.isDeleted() }?.nickname ?: WITHDRAWN_MEMBER_NAME,
                role = RoundFeedbackAuthorRole.PARTICIPANT,
            ),
            content = content.takeIf { revealed },
            revealed = revealed,
        )
    }

    private companion object {
        const val WITHDRAWN_MEMBER_NAME = "탈퇴 회원"
    }
}
