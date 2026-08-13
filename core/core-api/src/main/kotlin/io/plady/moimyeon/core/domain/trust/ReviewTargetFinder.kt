package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ReviewTargetFinder(
    private val roomFinder: RoomFinder,
    private val roomProgressReader: RoomProgressReader,
    private val reviewRepository: ReviewRepository,
    private val eligibilityValidator: ReviewEligibilityValidator,
) {
    @Transactional(readOnly = true)
    fun getTargets(authorMemberId: UUID, roomId: UUID): List<ReviewTarget> {
        val room = roomFinder.getRoom(roomId)
        eligibilityValidator.validateRoom(room.status)

        val attendances = roomProgressReader.getAttendances(roomId)
        eligibilityValidator.validateAuthorAttendance(
            attendances.firstOrNull { it.memberId == authorMemberId }?.status,
        )
        val submittedTargetIds = reviewRepository
            .findByRoomIdAndAuthorMemberIdAndDeletedAtIsNull(roomId, authorMemberId)
            .mapTo(mutableSetOf()) { it.targetMemberId }

        return attendances
            .filter {
                it.memberId != authorMemberId && eligibilityValidator.isEligibleAttendance(it.status)
            }
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
