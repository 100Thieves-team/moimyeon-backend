package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.ParticipationRole
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ParticipationRepository : JpaRepository<ParticipationEntity, Long> {
    // 방장 판별: 이 룸에 (회원, HOST) 활성 참여가 있는가.
    fun existsByRoomIdAndMemberIdAndParticipationRoleAndDeletedAtIsNull(
        roomId: UUID,
        memberId: UUID,
        participationRole: ParticipationRole,
    ): Boolean

    // 현재 인원 = 활성 참여 수.
    fun countByRoomIdAndDeletedAtIsNull(roomId: UUID): Long

    // 방장 참여 행(방장 회원 식별자 조회용).
    fun findFirstByRoomIdAndParticipationRoleAndDeletedAtIsNull(
        roomId: UUID,
        participationRole: ParticipationRole,
    ): ParticipationEntity?
}
