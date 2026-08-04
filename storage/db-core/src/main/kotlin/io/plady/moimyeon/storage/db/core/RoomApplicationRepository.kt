package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoomApplicationStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoomApplicationRepository : JpaRepository<RoomApplicationEntity, Long> {
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

    // 수락·반려 대상 로드. 신청이 해당 룸 소속이고 운영이 걷어내지 않았을 때만 처리한다.
    fun findByIdAndRoomIdAndDeletedAtIsNull(id: Long, roomId: UUID): RoomApplicationEntity?

    // 방장용 신청 목록(「룸 참여」 §4.3). 철회된 신청은 방장 처리 대상에서 제외한다(§4.4). 오래된 신청부터.
    fun findByRoomIdAndStatusNotAndDeletedAtIsNullOrderByAppliedAtAsc(
        roomId: UUID,
        status: RoomApplicationStatus,
    ): List<RoomApplicationEntity>
}
