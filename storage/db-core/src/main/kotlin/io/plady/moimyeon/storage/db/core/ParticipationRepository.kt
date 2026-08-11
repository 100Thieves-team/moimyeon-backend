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

    // 룸 취소 가능 여부(MOI-396). 방장도 참여 행을 갖기 때문에 전체 인원으로 재면 "== 1" 을 비교하게 된다.
    // 질문이 "몇 명인가"가 아니라 "있는가"이므로 세지 않고 첫 행에서 멈춘다.
    fun existsByRoomIdAndParticipationRoleAndStatusAndDeletedAtIsNull(
        roomId: UUID,
        participationRole: ParticipationRole,
        status: ParticipationStatus,
    ): Boolean

    // 탐색 목록의 표시용 일괄 집계(MOI-383 §4.1). 한 페이지 분량의 roomId 에만 IN 으로 걸어
    // 룸 수에 비례해 쿼리가 늘지 않게 한다. 기준은 단건 조회·정원 확정과 같은 "활성 참여"다.
    // 참여가 없는 룸은 GROUP BY 결과에 아예 없으므로 0 으로 채우는 것은 호출자의 몫이다.
    @Query(
        """
        select new io.plady.moimyeon.storage.db.core.RoomCount(p.roomId, count(p))
        from ParticipationEntity p
        where p.roomId in :roomIds
          and p.status = io.plady.moimyeon.core.enums.ParticipationStatus.JOINED
          and p.deletedAt is null
        group by p.roomId
        """,
    )
    fun countActiveByRoomIds(@Param("roomIds") roomIds: Collection<UUID>): List<RoomCount>

    fun countByRoomIdAndStatusAndDeletedAtIsNull(
        roomId: UUID,
        status: ParticipationStatus,
    ): Long

    fun existsByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
        roomId: UUID,
        memberId: UUID,
        status: ParticipationStatus,
    ): Boolean

    @Query(
        value = """
            select case when count(*) > 0 then true else false end
            from participation p
            join room_status_log rsl on rsl.room_id = p.room_id
            where p.room_id = :roomId
              and p.member_id = :memberId
              and p.deleted_at is null
              and rsl.transition_type = 'CONFIRMED'
              and rsl.deleted_at is null
              and p.joined_at <= rsl.occurred_at
              and (p.left_at is null or p.left_at > rsl.occurred_at)
        """,
        nativeQuery = true,
    )
    fun existsAtRoomConfirmation(
        @Param("roomId") roomId: UUID,
        @Param("memberId") memberId: UUID,
    ): Boolean

    @Query(
        value = """
            select p.*
            from participation p
            join room_status_log rsl on rsl.room_id = p.room_id
            where p.room_id = :roomId
              and p.deleted_at is null
              and rsl.transition_type = 'CONFIRMED'
              and rsl.deleted_at is null
              and p.joined_at <= rsl.occurred_at
              and (p.left_at is null or p.left_at > rsl.occurred_at)
            order by p.joined_at asc, p.id asc
        """,
        nativeQuery = true,
    )
    fun findAllAtRoomConfirmation(@Param("roomId") roomId: UUID): List<ParticipationEntity>

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
