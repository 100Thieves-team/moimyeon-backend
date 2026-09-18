package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.domain.room.RoomFinder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ReviewTargetFinder(
    private val roomFinder: RoomFinder,
    private val roomProgressReader: RoomProgressReader,
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
        return attendances
            .filter {
                it.memberId != authorMemberId && eligibilityValidator.isEligibleAttendance(it.status)
            }
            .map { attendance -> ReviewTarget(memberId = attendance.memberId) }
    }
}
