package io.plady.moimyeon.core.domain.trust

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class ReviewEligibilityValidator(
    private val roomFinder: RoomFinder,
    private val roomProgressReader: RoomProgressReader,
) {
    fun validate(roomId: UUID, authorMemberId: UUID, targetMemberId: UUID) {
        log.debug { "review-eligibility.validator.validate.byRoom roomId=$roomId authorMemberId=$authorMemberId targetMemberId=$targetMemberId" }
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
        log.debug { "review-eligibility.validator.validate.byStatus roomStatus=$roomStatus authorMemberId=$authorMemberId targetMemberId=$targetMemberId authorAttendanceStatus=$authorAttendanceStatus targetAttendanceStatus=$targetAttendanceStatus" }
        validateRoom(roomStatus)
        validateAuthorAttendance(authorAttendanceStatus)
        requireBusiness(authorMemberId != targetMemberId, CoreErrorType.REVIEW_SELF_NOT_ALLOWED)
        requireBusiness(
            isEligibleAttendance(targetAttendanceStatus),
            CoreErrorType.REVIEW_TARGET_NOT_ATTENDED,
        )
    }

    fun validateRoom(roomStatus: RoomStatus) {
        log.debug { "review-eligibility.validator.validateRoom roomStatus=$roomStatus" }
        requireBusiness(roomStatus == RoomStatus.COMPLETED, CoreErrorType.REVIEW_NOT_AVAILABLE)
    }

    fun validateAuthorAttendance(attendanceStatus: AttendanceStatus?) {
        log.debug { "review-eligibility.validator.validateAuthorAttendance attendanceStatus=$attendanceStatus" }
        requireBusiness(isEligibleAttendance(attendanceStatus), CoreErrorType.REVIEW_AUTHOR_NOT_ATTENDED)
    }

    fun isEligibleAttendance(status: AttendanceStatus?): Boolean {
        log.debug { "review-eligibility.validator.isEligibleAttendance status=$status" }
        return status == AttendanceStatus.ATTENDED
    }
}
