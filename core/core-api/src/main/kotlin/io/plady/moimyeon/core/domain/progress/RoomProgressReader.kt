package io.plady.moimyeon.core.domain.progress

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class RoomProgressReader(
    private val attendanceRepository: AttendanceRepository,
) {
    fun isAttended(roomId: UUID, memberId: UUID): Boolean {
        log.debug { "room-progress.reader.isAttended roomId=$roomId memberId=$memberId" }
        return attendanceRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId)
            ?.status == AttendanceStatus.ATTENDED
    }

    fun getAttendance(roomId: UUID, memberId: UUID): Attendance {
        log.debug { "room-progress.reader.getAttendance roomId=$roomId memberId=$memberId" }
        val attendance = requireFound(
            findAttendance(roomId, memberId),
            CoreErrorType.ROOM_PROGRESS_ATTENDANCE_NOT_FOUND,
        )
        return attendance
    }

    fun findAttendance(roomId: UUID, memberId: UUID): Attendance? {
        log.debug { "room-progress.reader.findAttendance roomId=$roomId memberId=$memberId" }
        val attendance = attendanceRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId)
            ?: return null
        return Attendance(
            memberId = attendance.memberId,
            status = attendance.status,
        )
    }

    fun getAttendances(roomId: UUID): List<Attendance> {
        log.debug { "room-progress.reader.getAttendances roomId=$roomId" }
        return attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)
            .map { attendance ->
                Attendance(
                    memberId = attendance.memberId,
                    status = attendance.status,
                )
            }
    }
}
