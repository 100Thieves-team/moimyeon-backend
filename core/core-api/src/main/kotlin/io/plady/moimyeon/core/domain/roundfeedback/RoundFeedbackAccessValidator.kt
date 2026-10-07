package io.plady.moimyeon.core.domain.roundfeedback

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class RoundFeedbackAccessValidator(
    private val roomFinder: RoomFinder,
    private val participationFinder: ParticipationFinder,
    private val clock: Clock,
) {
    fun validateOtherParticipantWriter(
        roomId: UUID,
        memberId: UUID,
        intervieweeMemberId: UUID,
    ) {
        log.debug { "round-feedback-access.validator.validateOtherParticipantWriter roomId=$roomId memberId=$memberId intervieweeMemberId=$intervieweeMemberId" }
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
        log.debug { "round-feedback-access.validator.validateIntervieweeWriter roomId=$roomId memberId=$memberId intervieweeMemberId=$intervieweeMemberId" }
        validateEditableRoom(roomId)
        validateInterviewee(roomId, memberId, intervieweeMemberId)
    }

    fun validateIntervieweeViewer(
        roomId: UUID,
        memberId: UUID,
        intervieweeMemberId: UUID,
    ) {
        log.debug { "round-feedback-access.validator.validateIntervieweeViewer roomId=$roomId memberId=$memberId intervieweeMemberId=$intervieweeMemberId" }
        val room = roomFinder.getRoom(roomId)
        requireBusiness(
            room.status == RoomStatus.COMPLETED || room.isProgressAvailable(LocalDateTime.now(clock)),
            CoreErrorType.ROUND_FEEDBACK_NOT_VIEWABLE,
        )
        validateInterviewee(roomId, memberId, intervieweeMemberId)
    }

    private fun validateEditableRoom(roomId: UUID) {
        requireBusiness(
            roomFinder.getRoom(roomId).isProgressAvailable(LocalDateTime.now(clock)),
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
}
