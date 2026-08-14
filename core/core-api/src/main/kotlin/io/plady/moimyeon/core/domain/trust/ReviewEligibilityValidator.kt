package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ReviewEligibilityValidator(
    private val roomFinder: RoomFinder,
    private val roomProgressReader: RoomProgressReader,
) {
    fun validate(roomId: UUID, authorMemberId: UUID, targetMemberId: UUID) {
        val room = roomFinder.getRoom(roomId)
        validateRoom(room.status)
        validateAuthorAttendance(roomProgressReader.findAttendance(roomId, authorMemberId)?.status)
        requireBusiness(authorMemberId != targetMemberId, CoreErrorType.REVIEW_SELF_NOT_ALLOWED)
        requireBusiness(
            isEligibleAttendance(roomProgressReader.findAttendance(roomId, targetMemberId)?.status),
            CoreErrorType.REVIEW_TARGET_NOT_ATTENDED,
        )
    }

    fun validate(
        roomStatus: RoomStatus,
        authorMemberId: UUID,
        targetMemberId: UUID,
        authorAttendanceStatus: AttendanceStatus?,
        targetAttendanceStatus: AttendanceStatus?,
    ) {
        validateRoom(roomStatus)
        validateAuthorAttendance(authorAttendanceStatus)
        requireBusiness(authorMemberId != targetMemberId, CoreErrorType.REVIEW_SELF_NOT_ALLOWED)
        requireBusiness(
            isEligibleAttendance(targetAttendanceStatus),
            CoreErrorType.REVIEW_TARGET_NOT_ATTENDED,
        )
    }

    fun validateRoom(roomStatus: RoomStatus) {
        requireBusiness(roomStatus == RoomStatus.COMPLETED, CoreErrorType.REVIEW_NOT_AVAILABLE)
    }

    fun validateAuthorAttendance(attendanceStatus: AttendanceStatus?) {
        requireBusiness(isEligibleAttendance(attendanceStatus), CoreErrorType.REVIEW_AUTHOR_NOT_ATTENDED)
    }

    fun isEligibleAttendance(status: AttendanceStatus?): Boolean = status == AttendanceStatus.ATTENDED
}
