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
        requireBusiness(room.status == RoomStatus.COMPLETED, CoreErrorType.REVIEW_NOT_AVAILABLE)
        requireBusiness(
            roomProgressReader.findAttendance(roomId, authorMemberId)?.status == AttendanceStatus.ATTENDED,
            CoreErrorType.REVIEW_AUTHOR_NOT_ATTENDED,
        )
        requireBusiness(authorMemberId != targetMemberId, CoreErrorType.REVIEW_SELF_NOT_ALLOWED)
        requireBusiness(
            roomProgressReader.findAttendance(roomId, targetMemberId)?.status == AttendanceStatus.ATTENDED,
            CoreErrorType.REVIEW_TARGET_NOT_ATTENDED,
        )
    }
}
