package io.plady.moimyeon.core.domain.trust

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.domain.room.RoomFinder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class ReviewTargetFinder(
    private val roomFinder: RoomFinder,
    private val roomProgressReader: RoomProgressReader,
    private val eligibilityValidator: ReviewEligibilityValidator,
) {
    @Transactional(readOnly = true)
    fun getTargets(authorMemberId: UUID, roomId: UUID): List<ReviewTarget> {
        log.debug { "review-target.finder.getTargets authorMemberId=$authorMemberId roomId=$roomId" }
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
