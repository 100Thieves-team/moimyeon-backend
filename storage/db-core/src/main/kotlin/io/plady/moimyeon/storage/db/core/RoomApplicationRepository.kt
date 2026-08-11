package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoomApplicationStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface RoomApplicationRepository : JpaRepository<RoomApplicationEntity, Long> {
    fun findFirstByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
        roomId: UUID,
        applicantMemberId: UUID,
    ): RoomApplicationEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstForUpdateByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
        roomId: UUID,
        applicantMemberId: UUID,
    ): RoomApplicationEntity?

    fun countByApplicantMemberIdAndStatusAndDeletedAtIsNull(
        applicantMemberId: UUID,
        status: RoomApplicationStatus,
    ): Long

    fun existsByRoomIdAndPendingMemberIdAndDeletedAtIsNull(
        roomId: UUID,
        pendingMemberId: UUID,
    ): Boolean

    fun existsByRoomIdAndApplicantMemberIdAndStatusAndDeletedAtIsNull(
        roomId: UUID,
        applicantMemberId: UUID,
        status: RoomApplicationStatus,
    ): Boolean

    // 수락·반려도 철회와 같은 신청 상태를 바꾸므로 신청 행 잠금 안에서 PENDING 여부를 판정한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByIdAndRoomIdAndDeletedAtIsNull(id: Long, roomId: UUID): RoomApplicationEntity?

    // 방장용 신청 목록(「룸 참여」 §4.3). 철회된 신청은 방장 처리 대상에서 제외한다(§4.4). 오래된 신청부터.
    fun findByRoomIdAndStatusNotAndDeletedAtIsNullOrderByAppliedAtAsc(
        roomId: UUID,
        status: RoomApplicationStatus,
    ): List<RoomApplicationEntity>

    // 룸이 취소·확정될 때 남은 대기 신청을 한 번에 끝낸다(「룸 참여」 §4.9). status 만 갈아 끼우면
    // 취소(ROOM_CANCELED)와 확정(ROOM_CONFIRMED)이 같은 쿼리를 쓴다.
    //
    // 벌크라서 직접 챙겨야 하는 것이 둘 있다.
    //   - pendingMemberId 를 비우지 않으면 대기 유니크 자리가 잠긴 채 남아 재신청이 영영 막힌다.
    //     엔티티의 accept/reject/withdraw 가 지는 책임을 여기서는 쿼리가 진다.
    //   - updatedAt 은 @UpdateTimestamp 라 벌크에서 돌지 않으므로 set 절에 직접 넣는다.
    //
    // clearAutomatically 를 켜지 않는다 — 컨텍스트 전체가 비워지면 호출자가 잡고 있는 RoomEntity 가
    // 준영속이 되어 상태 전이가 사라진다. 대신 호출 트랜잭션이 RoomApplicationEntity 를 로드하지 않는다는
    // 것을 전제로 삼는다(RoomCancellationIT 가 이 전제를 지킨다).
    @Modifying(flushAutomatically = true)
    @Query(
        """
        update RoomApplicationEntity a
           set a.status = :status,
               a.pendingMemberId = null,
               a.handledAt = :now,
               a.updatedAt = :now
         where a.roomId = :roomId
           and a.status = io.plady.moimyeon.core.enums.RoomApplicationStatus.PENDING
           and a.deletedAt is null
        """,
    )
    fun closeAllPending(
        @Param("roomId") roomId: UUID,
        @Param("status") status: RoomApplicationStatus,
        @Param("now") now: LocalDateTime,
    ): Int

    // 탐색 목록의 "신청 대기 수"(MOI-383 §4.1, 2026-08-04 PRD 갱신). 정렬에 쓰이지 않는 표시용이라
    // 한 페이지 분량의 roomId 에만 IN 으로 건다. 대기 신청이 없는 룸은 결과에 없으므로 0 은 호출자가 채운다.
    @Query(
        """
        select new io.plady.moimyeon.storage.db.core.RoomCount(a.roomId, count(a))
        from RoomApplicationEntity a
        where a.roomId in :roomIds
          and a.status = io.plady.moimyeon.core.enums.RoomApplicationStatus.PENDING
          and a.deletedAt is null
        group by a.roomId
        """,
    )
    fun countPendingByRoomIds(@Param("roomIds") roomIds: Collection<UUID>): List<RoomCount>
}
