package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class QuestionPreparationAccessValidator(
    private val roomFinder: RoomFinder,
    private val participationFinder: ParticipationFinder,
) {
    fun validateAuthor(roomId: UUID, authorMemberId: UUID) {
        val room = roomFinder.getRoom(roomId)
        requireBusiness(
            room.status == RoomStatus.CONFIRMED,
            CoreErrorType.QUESTION_PREPARATION_NOT_OPEN,
        )
        requireBusiness(
            participationFinder.isParticipating(roomId, authorMemberId),
            CoreErrorType.QUESTION_PREPARATION_FORBIDDEN,
        )
    }

    fun validateTarget(roomId: UUID, authorMemberId: UUID, targetMemberId: UUID) {
        requireBusiness(
            authorMemberId != targetMemberId,
            CoreErrorType.QUESTION_PREPARATION_FORBIDDEN,
        )
        requireBusiness(
            participationFinder.wasConfirmedParticipant(roomId, targetMemberId),
            CoreErrorType.QUESTION_TARGET_NOT_FOUND,
        )
    }
}
