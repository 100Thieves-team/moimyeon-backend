package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.AttendanceStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AttendanceRepository : JpaRepository<AttendanceEntity, Long> {
    fun findByRoomIdInAndDeletedAtIsNull(roomIds: Collection<UUID>): List<AttendanceEntity>

    fun findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId: UUID, memberId: UUID): AttendanceEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByRoomIdAndMemberIdAndDeletedAtIsNull(roomId: UUID, memberId: UUID): AttendanceEntity?

    fun findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId: UUID): List<AttendanceEntity>

    @Query(
        """
        SELECT a.memberId AS memberId, COUNT(a) AS count
        FROM AttendanceEntity a, RoomEntity r
        WHERE r.id = a.roomId
          AND r.status = io.plady.moimyeon.core.enums.RoomStatus.COMPLETED
          AND r.deletedAt IS NULL
          AND a.status = io.plady.moimyeon.core.enums.AttendanceStatus.ATTENDED
          AND a.deletedAt IS NULL
        GROUP BY a.memberId
        """,
    )
    fun countAttendedCompletedRoomsByMember(): List<MemberMetricCount>

    @Query(
        """
        SELECT a
        FROM AttendanceEntity a, RoomEntity r
        WHERE r.id = a.roomId
          AND a.memberId = :memberId
          AND r.status = io.plady.moimyeon.core.enums.RoomStatus.COMPLETED
          AND r.deletedAt IS NULL
          AND a.deletedAt IS NULL
        ORDER BY r.startAt DESC, r.id DESC
        """,
    )
    fun findRecentCompletedByMember(
        @Param("memberId") memberId: UUID,
        pageable: Pageable,
    ): List<AttendanceEntity>

    @Query(
        """
        SELECT COUNT(a)
        FROM AttendanceEntity a, RoomEntity r
        WHERE r.id = a.roomId
          AND a.memberId = :memberId
          AND a.status = :status
          AND r.status = io.plady.moimyeon.core.enums.RoomStatus.COMPLETED
          AND r.deletedAt IS NULL
          AND a.deletedAt IS NULL
        """,
    )
    fun countCompletedByMemberAndStatus(
        @Param("memberId") memberId: UUID,
        @Param("status") status: AttendanceStatus,
    ): Long
}
