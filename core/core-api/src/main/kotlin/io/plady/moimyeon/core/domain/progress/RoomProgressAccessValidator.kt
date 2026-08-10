package io.plady.moimyeon.core.domain.progress

import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomProgressAccessValidator(
    private val roomRepository: RoomRepository,
    private val participationFinder: ParticipationFinder,
) {
    fun validateStarter(roomId: UUID, memberId: UUID) {
        requireBusiness(
            findActiveRoomStatus(roomId) == RoomStatus.CONFIRMED,
            CoreErrorType.ROOM_PROGRESS_NOT_STARTABLE,
        )
        validateConfirmedParticipant(
            roomId = roomId,
            memberId = memberId,
            errorType = CoreErrorType.ROOM_PROGRESS_START_FORBIDDEN,
        )
    }

    fun validateAttendanceViewer(roomId: UUID, memberId: UUID) {
        requireBusiness(
            findActiveRoomStatus(roomId) in ATTENDANCE_VIEWABLE_STATUSES,
            CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE,
        )
        validateConfirmedParticipant(roomId, memberId, CoreErrorType.ROOM_PROGRESS_FORBIDDEN)
    }

    fun validateRailViewer(roomId: UUID, memberId: UUID) {
        requireBusiness(
            findActiveRoomStatus(roomId) == RoomStatus.IN_PROGRESS,
            CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE,
        )
        validateConfirmedParticipant(roomId, memberId, CoreErrorType.ROOM_PROGRESS_FORBIDDEN)
    }

    private fun findActiveRoomStatus(roomId: UUID): RoomStatus {
        val room = requireFound(
            roomRepository.findById(roomId).orElse(null),
            CoreErrorType.ROOM_NOT_FOUND,
        )
        requireBusiness(room.isActive(), CoreErrorType.ROOM_NOT_FOUND)
        return room.status
    }

    private fun validateConfirmedParticipant(
        roomId: UUID,
        memberId: UUID,
        errorType: CoreErrorType,
    ) {
        requireBusiness(
            participationFinder.wasConfirmedParticipant(roomId, memberId),
            errorType,
        )
    }

    private companion object {
        val ATTENDANCE_VIEWABLE_STATUSES = setOf(RoomStatus.IN_PROGRESS, RoomStatus.COMPLETED)
    }
}
