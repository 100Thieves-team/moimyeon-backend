package io.plady.moimyeon.core.domain.progress

import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

@Component
class RoomProgressAccessValidator(
    private val roomRepository: RoomRepository,
    private val participationFinder: ParticipationFinder,
) {
    fun validateStarter(roomId: UUID, memberId: UUID, at: LocalDateTime) {
        requireBusiness(
            findActiveRoom(roomId).canStartProgress(at),
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
            findActiveRoom(roomId).status in ATTENDANCE_VIEWABLE_STATUSES,
            CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE,
        )
        validateConfirmedParticipant(roomId, memberId, CoreErrorType.ROOM_PROGRESS_FORBIDDEN)
    }

    fun validateRailViewer(roomId: UUID, memberId: UUID) {
        // 출석은 완료 후에도 남는 기록이지만, 레일은 진행 중 화면을 위한 도구라 완료와 함께 닫는다.
        requireBusiness(
            findActiveRoom(roomId).status == RoomStatus.IN_PROGRESS,
            CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE,
        )
        validateConfirmedParticipant(roomId, memberId, CoreErrorType.ROOM_PROGRESS_FORBIDDEN)
    }

    private fun findActiveRoom(roomId: UUID): RoomEntity {
        val room = requireFound(
            roomRepository.findById(roomId).orElse(null),
            CoreErrorType.ROOM_NOT_FOUND,
        )
        requireBusiness(room.isActive(), CoreErrorType.ROOM_NOT_FOUND)
        return room
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
