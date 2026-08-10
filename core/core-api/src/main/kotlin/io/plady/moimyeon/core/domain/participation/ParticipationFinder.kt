package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ParticipationFinder(
    private val participationRepository: ParticipationRepository,
) {
    fun isParticipating(roomId: UUID, memberId: UUID): Boolean {
        return participationRepository.existsByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
            roomId,
            memberId,
            ParticipationStatus.JOINED,
        )
    }

    fun wasConfirmedParticipant(roomId: UUID, memberId: UUID): Boolean {
        return participationRepository.existsAtRoomConfirmation(roomId, memberId)
    }

    fun getConfirmedParticipantIds(roomId: UUID): List<UUID> {
        return participationRepository.findAllAtRoomConfirmation(roomId).map { it.memberId }
    }

    fun getHostMemberId(roomId: UUID): UUID {
        return requireFound(
            participationRepository.findFirstByRoomIdAndParticipationRoleAndDeletedAtIsNull(
                roomId,
                ParticipationRole.HOST,
            ),
            CoreErrorType.ROOM_NOT_FOUND,
        ).memberId
    }
}
