package io.plady.moimyeon.core.domain.roundfeedback

import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoundFeedbackAccessValidator(
    private val roomFinder: RoomFinder,
    private val participationFinder: ParticipationFinder,
) {
    fun validateOtherParticipantWriter(
        roomId: UUID,
        memberId: UUID,
        intervieweeMemberId: UUID,
    ) {
        validateEditableRoom(roomId)
        validateConfirmedParticipant(roomId, memberId)
        validateConfirmedParticipant(roomId, intervieweeMemberId)
        requireBusiness(memberId != intervieweeMemberId, CoreErrorType.ROUND_FEEDBACK_FORBIDDEN)
    }

    fun validateIntervieweeWriter(
        roomId: UUID,
        memberId: UUID,
        intervieweeMemberId: UUID,
    ) {
        validateEditableRoom(roomId)
        validateInterviewee(roomId, memberId, intervieweeMemberId)
    }

    fun validateIntervieweeViewer(
        roomId: UUID,
        memberId: UUID,
        intervieweeMemberId: UUID,
    ) {
        requireBusiness(
            roomFinder.getRoom(roomId).status in VIEWABLE_STATUSES,
            CoreErrorType.ROUND_FEEDBACK_NOT_VIEWABLE,
        )
        validateInterviewee(roomId, memberId, intervieweeMemberId)
    }

    private fun validateEditableRoom(roomId: UUID) {
        requireBusiness(
            roomFinder.getRoom(roomId).status == RoomStatus.IN_PROGRESS,
            CoreErrorType.ROUND_FEEDBACK_NOT_EDITABLE,
        )
    }

    private fun validateInterviewee(roomId: UUID, memberId: UUID, intervieweeMemberId: UUID) {
        validateConfirmedParticipant(roomId, intervieweeMemberId)
        requireBusiness(memberId == intervieweeMemberId, CoreErrorType.ROUND_FEEDBACK_FORBIDDEN)
    }

    private fun validateConfirmedParticipant(roomId: UUID, memberId: UUID) {
        requireBusiness(
            participationFinder.wasConfirmedParticipant(roomId, memberId),
            CoreErrorType.ROUND_FEEDBACK_FORBIDDEN,
        )
    }

    private companion object {
        val VIEWABLE_STATUSES = setOf(RoomStatus.IN_PROGRESS, RoomStatus.COMPLETED)
    }
}
