package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class QuestionCommentAccessValidator(
    private val roomFinder: RoomFinder,
    private val participationFinder: ParticipationFinder,
) {
    fun validateWriter(roomId: UUID, memberId: UUID, targetMemberId: UUID) {
        val room = roomFinder.getRoom(roomId)
        requireBusiness(
            room.status == RoomStatus.IN_PROGRESS,
            CoreErrorType.QUESTION_COMMENT_NOT_EDITABLE,
        )
        validateConfirmedParticipant(roomId, memberId)
        validateConfirmedParticipant(roomId, targetMemberId)
        requireBusiness(memberId != targetMemberId, CoreErrorType.QUESTION_COMMENT_FORBIDDEN)
    }

    fun validateViewer(roomId: UUID, memberId: UUID, targetMemberId: UUID) {
        val room = roomFinder.getRoom(roomId)
        requireBusiness(
            room.status == RoomStatus.IN_PROGRESS || room.status == RoomStatus.COMPLETED,
            CoreErrorType.QUESTION_COMMENT_NOT_VIEWABLE,
        )
        validateConfirmedParticipant(roomId, memberId)
        validateConfirmedParticipant(roomId, targetMemberId)
        if (room.status == RoomStatus.IN_PROGRESS) {
            requireBusiness(memberId != targetMemberId, CoreErrorType.QUESTION_COMMENT_FORBIDDEN)
        }
    }

    private fun validateConfirmedParticipant(roomId: UUID, memberId: UUID) {
        requireBusiness(
            participationFinder.wasConfirmedParticipant(roomId, memberId),
            CoreErrorType.QUESTION_COMMENT_FORBIDDEN,
        )
    }
}
