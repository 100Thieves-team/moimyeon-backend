package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AttendanceRepository : JpaRepository<AttendanceEntity, Long> {
    fun findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId: UUID, memberId: UUID): AttendanceEntity?

    fun findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId: UUID): List<AttendanceEntity>
}
