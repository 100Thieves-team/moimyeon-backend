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
    // 참여 슬롯 게이트(「룸 참여」 §4.1, MOI-427). 규칙은 ParticipationSlot 이 갖고 여기는 막기만 한다 —
    // 같은 판정의 묻는 형태는 ParticipationFinder.hasAvailableSlot 이다(수락·자동 위임·룸 상세가 쓴다).
    fun validateSlotAvailable(memberId: UUID) {
        val occupied = participationRepository.countOccupiedSlotsByMemberId(
            memberId,
            ParticipationSlot.OCCUPYING_ROOM_STATUSES,
        )
        requireBusiness(ParticipationSlot.isAvailable(occupied), CoreErrorType.PARTICIPATION_SLOT_EXCEEDED)
    }

    fun validateHost(roomId: UUID, memberId: UUID) {
        requireBusiness(
            participationRepository.existsByRoomIdAndMemberIdAndParticipationRoleAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationRole.HOST,
                ParticipationStatus.JOINED,
            ),
            CoreErrorType.ROOM_FORBIDDEN,
        )
    }

    // 방장·참여자 공통 게이트. 방장도 JOINED 참여 행을 가지므로 역할은 보지 않는다.
    // validateHost 와 다르다 — 여기는 참여자도 통과한다(「룸 참여」 §3).
    fun validateParticipant(roomId: UUID, memberId: UUID) {
        requireBusiness(
            participationRepository.existsByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationStatus.JOINED,
            ),
            CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN,
        )
    }

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
