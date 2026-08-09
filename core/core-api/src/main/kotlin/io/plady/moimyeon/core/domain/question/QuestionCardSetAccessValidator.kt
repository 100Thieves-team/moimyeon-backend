package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class QuestionCardSetAccessValidator(
    private val roomFinder: RoomFinder,
    private val participationFinder: ParticipationFinder,
) {
    fun validateViewer(roomId: UUID, memberId: UUID) {
        val room = roomFinder.getRoom(roomId).room
        requireBusiness(
            room.status == RoomStatus.CONFIRMED || room.status == RoomStatus.COMPLETED,
            CoreErrorType.QUESTION_CARD_SET_NOT_OPEN,
        )
        requireBusiness(
            participationFinder.isParticipating(roomId, memberId),
            CoreErrorType.QUESTION_CARD_SET_FORBIDDEN,
        )
    }

    fun validateOtherCardSetTarget(roomId: UUID, requesterMemberId: UUID, targetMemberId: UUID) {
        requireBusiness(
            requesterMemberId != targetMemberId,
            CoreErrorType.QUESTION_CARD_SET_FORBIDDEN,
        )
        requireBusiness(
            participationFinder.wasConfirmedParticipant(roomId, targetMemberId),
            CoreErrorType.QUESTION_CARD_SET_NOT_FOUND,
        )
    }
}
