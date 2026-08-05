package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoomApplicationStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
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
}
