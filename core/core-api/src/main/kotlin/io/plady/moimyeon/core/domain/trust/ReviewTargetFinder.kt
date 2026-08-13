package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ReviewTargetFinder(
    private val roomFinder: RoomFinder,
    private val roomProgressReader: RoomProgressReader,
    private val reviewRepository: ReviewRepository,
) {
    @Transactional(readOnly = true)
    fun getTargets(authorMemberId: UUID, roomId: UUID): List<ReviewTarget> {
        val room = roomFinder.getRoom(roomId)
        requireBusiness(room.status == RoomStatus.COMPLETED, CoreErrorType.REVIEW_NOT_AVAILABLE)

        val attendances = roomProgressReader.getAttendances(roomId)
        requireBusiness(
            attendances.any {
                it.memberId == authorMemberId && it.status == AttendanceStatus.ATTENDED
            },
            CoreErrorType.REVIEW_AUTHOR_NOT_ATTENDED,
        )
        val submittedTargetIds = reviewRepository
            .findByRoomIdAndAuthorMemberIdAndDeletedAtIsNull(roomId, authorMemberId)
            .mapTo(mutableSetOf()) { it.targetMemberId }

        return attendances
            .filter { it.memberId != authorMemberId && it.status == AttendanceStatus.ATTENDED }
            .map { attendance ->
                ReviewTarget(
                    memberId = attendance.memberId,
                    status = if (attendance.memberId in submittedTargetIds) {
                        ReviewTargetStatus.SUBMITTED
                    } else {
                        ReviewTargetStatus.WRITABLE
                    },
                )
            }
    }
}
