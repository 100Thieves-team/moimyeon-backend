package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoomApplicationStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
