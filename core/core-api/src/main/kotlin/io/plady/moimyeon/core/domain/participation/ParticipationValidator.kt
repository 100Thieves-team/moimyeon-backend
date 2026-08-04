package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ParticipationValidator(
    private val participationRepository: ParticipationRepository,
) {
    fun validateNotHost(roomId: UUID, memberId: UUID) {
        requireBusiness(
            !participationRepository.existsByRoomIdAndMemberIdAndParticipationRoleAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationRole.HOST,
                ParticipationStatus.JOINED,
            ),
            CoreErrorType.ROOM_HOST_CANNOT_APPLY,
        )
    }

    fun validateNotParticipating(roomId: UUID, memberId: UUID) {
        requireBusiness(
            !participationRepository.existsByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationStatus.JOINED,
            ),
            CoreErrorType.ROOM_APPLICATION_DUPLICATED,
        )
    }

    fun validateNoRemovalHistory(roomId: UUID, memberId: UUID) {
        requireBusiness(
            !participationRepository.existsRemovalHistory(roomId, memberId),
            CoreErrorType.ROOM_REAPPLICATION_NOT_ALLOWED,
        )
    }
}
