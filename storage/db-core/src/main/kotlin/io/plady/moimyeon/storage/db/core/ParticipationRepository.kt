package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    fun countByRoomIdAndStatusAndDeletedAtIsNull(
        roomId: UUID,
        status: ParticipationStatus,
    ): Long

    fun existsByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
        roomId: UUID,
        memberId: UUID,
        status: ParticipationStatus,
    ): Boolean

    fun existsByRoomIdAndMemberIdAndParticipationRoleAndStatusAndDeletedAtIsNull(
        roomId: UUID,
        memberId: UUID,
        participationRole: ParticipationRole,
        status: ParticipationStatus,
    ): Boolean

    // LEFT 처리자가 본인이 아니면 방장에 의해 내보내진 이력이다.
    @Query(
        """
        select case when count(p) > 0 then true else false end
        from ParticipationEntity p
        where p.roomId = :roomId
          and p.memberId = :memberId
          and p.status = io.plady.moimyeon.core.enums.ParticipationStatus.LEFT
          and p.leftByMemberId is not null
          and p.leftByMemberId <> p.memberId
        """,
    )
    fun existsRemovalHistory(
        @Param("roomId") roomId: UUID,
        @Param("memberId") memberId: UUID,
    ): Boolean

    // 방장 참여 행(방장 회원 식별자 조회용).
    fun findFirstByRoomIdAndParticipationRoleAndDeletedAtIsNull(
        roomId: UUID,
        participationRole: ParticipationRole,
    ): ParticipationEntity?
}
