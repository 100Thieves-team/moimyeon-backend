package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.domain.trust.RoomReviewFinder
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class RoomParticipationReader(
    private val participationRepository: ParticipationRepository,
    private val roomFinder: RoomFinder,
    private val roomReviewFinder: RoomReviewFinder,
) {
    @Transactional(readOnly = true)
    fun getHistory(memberId: UUID): RoomParticipationHistory {
        val roomIds = participationRepository
            .findByMemberIdAndStatusAndDeletedAtIsNull(memberId, ParticipationStatus.JOINED)
            .map { it.roomId }
        val roomSummaries = roomFinder.getSummaries(roomIds)

        val participatingRooms = roomSummaries
            .filter { it.room.status in PARTICIPATING_ROOM_STATUSES }
            .map { ParticipatingRoom(it.room, it.participantCount) }
            .sortedWith(compareBy<ParticipatingRoom> { it.room.schedule.startAt }.thenBy { it.room.id })

        val completedRoomSummaries = roomSummaries.filter { it.room.status == RoomStatus.COMPLETED }
        val reviewSummaries = roomReviewFinder.getSummaries(memberId, completedRoomSummaries.map { it.room.id })
        val completedRooms = completedRoomSummaries
            .map { roomSummary ->
                val review = reviewSummaries.getValue(roomSummary.room.id)
                CompletedRoomParticipation(
                    room = roomSummary.room,
                    attendedParticipantCount = review.attendedParticipantCount,
                    reviewStatus = review.status,
                )
            }
            .sortedWith(
                compareByDescending<CompletedRoomParticipation> { it.room.schedule.startAt }
                    .thenByDescending { it.room.id },
            )

        return RoomParticipationHistory(participatingRooms, completedRooms)
    }

    private companion object {
        val PARTICIPATING_ROOM_STATUSES = setOf(
            RoomStatus.RECRUITING,
            RoomStatus.CONFIRMED,
            RoomStatus.IN_PROGRESS,
        )
    }
}
