package io.plady.moimyeon.core.domain.roundfeedback

import io.plady.moimyeon.core.domain.member.MemberAttribution
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.question.QuestionMemoRecordReader
import io.plady.moimyeon.core.enums.RoundFeedbackType
import io.plady.moimyeon.storage.db.core.RoundFeedbackEntity
import io.plady.moimyeon.storage.db.core.RoundFeedbackRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class RoundFeedbackReader(
    private val questionMemoRecordReader: QuestionMemoRecordReader,
    private val memberFinder: MemberFinder,
    private val feedbackRepository: RoundFeedbackRepository,
) {
    @Transactional(readOnly = true)
    fun getMyQuestionRecords(
        roomId: UUID,
        intervieweeMemberId: UUID,
        authorMemberId: UUID,
    ): List<RoundQuestionRecord> {
        return questionMemoRecordReader.getAskedRecordsByAuthor(
            roomId,
            intervieweeMemberId,
            authorMemberId,
        ).map { record ->
            RoundQuestionRecord(
                questionId = record.questionId,
                questionContent = record.questionContent,
                comments = record.comments.map { comment ->
                    RoundQuestionComment(
                        id = comment.id,
                        type = comment.type,
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
        val authorsById = memberFinder.getAttributionsIncludingWithdrawn(finalFeedbacks.map { it.authorMemberId })
            .associateBy { it.id }
        return IntervieweeRoundFeedback(
            selfFeedback = feedbacks.firstOrNull { it.feedbackType == RoundFeedbackType.SELF }
                ?.let { SelfFeedback(it.id, it.content) },
            finalFeedbacks = finalFeedbacks.map { it.toCard(authorsById[it.authorMemberId]) },
        )
    }

    private fun RoundFeedbackEntity.toCard(author: MemberAttribution?): FinalFeedbackCard {
        val revealed = disclosedAt != null
        return FinalFeedbackCard(
            id = id,
            author = RoundFeedbackAuthor(
                memberId = authorMemberId,
                displayName = author?.takeUnless { it.withdrawn }?.nickname ?: WITHDRAWN_MEMBER_NAME,
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
