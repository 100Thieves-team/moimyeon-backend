package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ParticipationFinder(
    private val participationRepository: ParticipationRepository,
) {
    fun getParticipatingRoomIds(memberId: UUID): List<UUID> {
        return participationRepository
            .findByMemberIdAndStatusAndDeletedAtIsNull(memberId, ParticipationStatus.JOINED)
            .map { it.roomId }
    }

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
        return checkNotNull(
            participationRepository.findFirstByRoomIdAndParticipationRoleAndStatusAndDeletedAtIsNull(
                roomId,
                ParticipationRole.HOST,
                ParticipationStatus.JOINED,
            ),
        ) {
            "확정 룸에는 HOST 참여 행이 있어야 합니다. roomId=$roomId"
        }.memberId
    }
}
