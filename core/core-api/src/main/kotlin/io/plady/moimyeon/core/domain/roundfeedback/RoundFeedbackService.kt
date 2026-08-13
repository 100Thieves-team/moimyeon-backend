package io.plady.moimyeon.core.domain.roundfeedback

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RoundFeedbackService(
    private val accessValidator: RoundFeedbackAccessValidator,
    private val feedbackReader: RoundFeedbackReader,
    private val feedbackManager: RoundFeedbackManager,
) {
    fun getMyQuestionRecords(
        memberId: UUID,
        roomId: UUID,
        intervieweeMemberId: UUID,
    ): List<RoundQuestionRecord> {
        accessValidator.validateOtherParticipantWriter(roomId, memberId, intervieweeMemberId)
        return feedbackReader.getMyQuestionRecords(roomId, intervieweeMemberId, memberId)
    }

    fun leaveFinalFeedback(
        memberId: UUID,
        roomId: UUID,
        intervieweeMemberId: UUID,
        content: String,
    ): Long {
        accessValidator.validateOtherParticipantWriter(roomId, memberId, intervieweeMemberId)
        return feedbackManager.registerFinalFeedback(
            RoundFeedbackCommand(
                roomId = roomId,
                intervieweeMemberId = intervieweeMemberId,
                authorMemberId = memberId,
                content = content,
            ),
        )
    }

    fun leaveSelfFeedback(
        memberId: UUID,
        roomId: UUID,
        intervieweeMemberId: UUID,
        content: String,
    ): Long {
        accessValidator.validateIntervieweeWriter(roomId, memberId, intervieweeMemberId)
        return feedbackManager.upsertSelfFeedback(
            RoundFeedbackCommand(
                roomId = roomId,
                intervieweeMemberId = intervieweeMemberId,
                authorMemberId = memberId,
                content = content,
            ),
        )
    }

    fun getIntervieweeFeedback(
        memberId: UUID,
        roomId: UUID,
        intervieweeMemberId: UUID,
    ): IntervieweeRoundFeedback {
        accessValidator.validateIntervieweeViewer(roomId, memberId, intervieweeMemberId)
        return feedbackReader.getIntervieweeFeedback(roomId, intervieweeMemberId)
    }

    fun confirmFinalFeedbackDisclosure(
        memberId: UUID,
        roomId: UUID,
        intervieweeMemberId: UUID,
        feedbackId: Long,
    ) {
        accessValidator.validateIntervieweeViewer(roomId, memberId, intervieweeMemberId)
        feedbackManager.confirmDisclosure(roomId, intervieweeMemberId, feedbackId)
    }
}
