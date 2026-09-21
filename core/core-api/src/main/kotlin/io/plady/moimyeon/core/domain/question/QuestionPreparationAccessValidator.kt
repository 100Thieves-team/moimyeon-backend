package io.plady.moimyeon.core.domain.question

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class QuestionPreparationAccessValidator(
    private val roomFinder: RoomFinder,
    private val participationFinder: ParticipationFinder,
) {
    fun validateAuthor(roomId: UUID, authorMemberId: UUID) {
        log.debug { "question-preparation-access.validator.validateAuthor roomId=$roomId authorMemberId=$authorMemberId" }
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
        log.debug { "question-preparation-access.validator.validateTarget roomId=$roomId authorMemberId=$authorMemberId targetMemberId=$targetMemberId" }
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
