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

    // 참여 슬롯 사용량(MOI-427). 위의 getParticipatingRoomIds 와 다르다 — 이쪽은 룸 상태를 걸러
    // "지금 슬롯을 물고 있는 룸"만 센다(ParticipationSlot). 목록용 조회와 한도 판정을 같은 함수로 쓰면
    // 취소·완료된 룸이 한도에 섞여 들어간다.
    fun countOccupiedSlots(memberId: UUID): Long {
        return participationRepository.countOccupiedSlotsByMemberId(
            memberId,
            ParticipationSlot.OCCUPYING_ROOM_STATUSES,
        )
    }

    // 막지 않고 묻는다 — MemberFinder.isActive 와 같은 성격이다(MOI-427). 위임 순회는 후보를 건너뛰어야 하고
    // 룸 상세(MOI-387)는 예외 없이 차단 사유만 표시해야 한다. 막는 쪽은 ParticipationValidator 가 갖는다.
    fun hasAvailableSlot(memberId: UUID): Boolean {
        return ParticipationSlot.isAvailable(countOccupiedSlots(memberId))
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
