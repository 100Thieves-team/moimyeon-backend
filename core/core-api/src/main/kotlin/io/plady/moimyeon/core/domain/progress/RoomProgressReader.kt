package io.plady.moimyeon.core.domain.progress

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomProgressReader(
    private val attendanceRepository: AttendanceRepository,
) {
    fun getAttendance(roomId: UUID, memberId: UUID): Attendance {
        val attendance = requireFound(
            attendanceRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId),
            CoreErrorType.ROOM_PROGRESS_ATTENDANCE_NOT_FOUND,
        )
        return Attendance(
            memberId = attendance.memberId,
            status = attendance.status,
        )
    }
}
