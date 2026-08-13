package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class RoomReviewFinder(
    private val attendanceRepository: AttendanceRepository,
    private val reviewRepository: ReviewRepository,
) {
    @Transactional(readOnly = true)
    fun getSummaries(memberId: UUID, roomIds: Collection<UUID>): Map<UUID, RoomReviewSummary> {
        if (roomIds.isEmpty()) return emptyMap()

        val attendancesByRoom = attendanceRepository
            .findByRoomIdInAndDeletedAtIsNull(roomIds)
            .groupBy { it.roomId }
        val reviewsByRoom = reviewRepository
            .findByRoomIdInAndAuthorMemberIdAndDeletedAtIsNull(roomIds, memberId)
            .groupBy { it.roomId }

        return roomIds.associateWith { roomId ->
            val attendances = attendancesByRoom[roomId].orEmpty()
            RoomReviewSummary(
                roomId = roomId,
                attendedParticipantCount = attendances.count { it.status == AttendanceStatus.ATTENDED },
                status = status(memberId, attendances, reviewsByRoom[roomId].orEmpty()),
            )
        }
    }

    private fun status(
        memberId: UUID,
        attendances: List<AttendanceEntity>,
        reviews: List<ReviewEntity>,
    ): RoomReviewStatus {
        val authorAttended = attendances.any {
            it.memberId == memberId && it.status == AttendanceStatus.ATTENDED
        }
        if (!authorAttended) return RoomReviewStatus.NOT_ELIGIBLE_ABSENT

        val eligibleTargetIds = attendances
            .filter { it.memberId != memberId && it.status == AttendanceStatus.ATTENDED }
            .mapTo(mutableSetOf()) { it.memberId }
        if (eligibleTargetIds.isEmpty()) return RoomReviewStatus.NOT_ELIGIBLE_NO_TARGET

        val writtenTargetIds = reviews.mapTo(mutableSetOf()) { it.targetMemberId }
        return if (writtenTargetIds.containsAll(eligibleTargetIds)) {
            RoomReviewStatus.WRITTEN
        } else {
            RoomReviewStatus.WRITABLE
        }
    }
}
