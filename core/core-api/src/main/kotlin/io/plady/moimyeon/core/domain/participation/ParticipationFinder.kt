package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.storage.db.core.ParticipationEntity
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

    // 조회 API 용 조립(MOI-447). 게이트(validateSlotAvailable)와 같은 카운트를 봐야
    // 안내(remaining=0)와 실제(신청·수락·생성 거부)가 어긋나지 않는다.
    fun getSlots(memberId: UUID): ParticipationSlots = ParticipationSlots.of(countOccupiedSlots(memberId))

    // 막지 않고 묻는다 — MemberFinder.isActive 와 같은 성격이다(MOI-427). 위임 순회는 후보를 건너뛰어야 하고
    // 룸 상세(MOI-387)는 예외 없이 차단 사유만 표시해야 한다. 막는 쪽은 ParticipationValidator 가 갖는다.
    fun hasAvailableSlot(memberId: UUID): Boolean {
        return ParticipationSlot.isAvailable(countOccupiedSlots(memberId))
    }

    // 룸 목록의 뷰어 관계 판정용(MOI-387). 룸 수에 비례해 쿼리가 늘지 않게 한 번에 읽는다.
    // 술어는 단건 판정과 같아야 한다 — 참여는 isParticipating, 강퇴는 existsRemovalHistory 와 같다.
    // 갈리면 목록과 상세가 서로 다른 관계를 말한다.
    fun getRoomParticipations(memberId: UUID, roomIds: Collection<UUID>): List<MemberRoomParticipation> {
        if (roomIds.isEmpty()) return emptyList()

        return participationRepository.findByMemberIdAndRoomIdIn(memberId, roomIds)
            .groupBy { it.roomId }
            .map { (roomId, rows) ->
                MemberRoomParticipation(
                    roomId = roomId,
                    host = rows.any { it.isJoined() && it.participationRole == ParticipationRole.HOST },
                    joined = rows.any { it.isJoined() },
                    removed = rows.any { it.isRemoval(memberId) },
                )
            }
    }

    // 방명록 작성자 뱃지 판정용(MOI-461). 작성자가 명단에 없으면 (퇴장), 방장 행이면 방장 뱃지다.
    // 술어는 validateParticipant 와 같은 JOINED 기준이어야 한다 - 갈리면 게이트는 통과한 사람이
    // 자기 글에서 (퇴장)으로 보이는 상태가 된다.
    fun getJoinedParticipants(roomId: UUID): List<JoinedParticipant> {
        return participationRepository
            .findByRoomIdAndStatusAndDeletedAtIsNullOrderByJoinedAtAscIdAsc(roomId, ParticipationStatus.JOINED)
            .map { JoinedParticipant(memberId = it.memberId, isHost = it.participationRole == ParticipationRole.HOST) }
    }

    fun isParticipating(roomId: UUID, memberId: UUID): Boolean {
        return participationRepository.existsByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
            roomId,
            memberId,
            ParticipationStatus.JOINED,
        )
    }

    fun wasConfirmedParticipant(roomId: UUID, memberId: UUID): Boolean {
        return participationRepository.countAtRoomConfirmation(roomId, memberId) > 0
    }

    fun getConfirmedParticipantIds(roomId: UUID): List<UUID> {
        return participationRepository.findAllAtRoomConfirmation(roomId).map { it.memberId }
    }

    // 엔티티는 Finder 밖으로 나가지 않으므로 술어도 여기 안에 둔다.
    private fun ParticipationEntity.isJoined(): Boolean = status == ParticipationStatus.JOINED && isActive()

    // existsRemovalHistory 와 같은 술어다 — LEFT 이고 처리자가 본인이 아니면 방장이 내보낸 것이다.
    // 그쪽처럼 deletedAt 을 보지 않는다. 강퇴 이력은 지워도 재신청이 열려서는 안 된다.
    private fun ParticipationEntity.isRemoval(memberId: UUID): Boolean = status == ParticipationStatus.LEFT &&
        leftByMemberId != null &&
        leftByMemberId != memberId

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
