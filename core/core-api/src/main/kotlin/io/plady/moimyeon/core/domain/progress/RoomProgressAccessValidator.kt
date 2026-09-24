package io.plady.moimyeon.core.domain.progress

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class RoomProgressAccessValidator(
    private val roomRepository: RoomRepository,
    private val participationFinder: ParticipationFinder,
    private val participationValidator: ParticipationValidator,
    private val clock: Clock,
) {
    fun validateAttendanceViewer(roomId: UUID, memberId: UUID) {
        log.debug { "room-progress-access.validator.validateAttendanceViewer roomId=$roomId memberId=$memberId" }
        requireBusiness(
            findActiveRoom(roomId).status in ATTENDANCE_VIEWABLE_STATUSES,
            CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE,
        )
        validateConfirmedParticipant(roomId, memberId, CoreErrorType.ROOM_PROGRESS_FORBIDDEN)
    }

    fun validateCompleter(roomId: UUID, memberId: UUID) {
        log.debug { "room-progress-access.validator.validateCompleter roomId=$roomId memberId=$memberId" }
        requireBusiness(
            findActiveRoom(roomId).canComplete(),
            CoreErrorType.ROOM_PROGRESS_NOT_COMPLETABLE,
        )
        participationValidator.validateHost(roomId, memberId)
    }

    fun validateAttendanceRecorder(roomId: UUID, memberId: UUID) {
        log.debug { "room-progress-access.validator.validateAttendanceRecorder roomId=$roomId memberId=$memberId" }
        requireBusiness(
            findActiveRoom(roomId).status == RoomStatus.COMPLETED,
            CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE,
        )
        participationValidator.validateHost(roomId, memberId)
    }

    fun validateInProgressParticipant(roomId: UUID, memberId: UUID) {
        requireBusiness(
            findActiveRoom(roomId).isProgressAvailable(LocalDateTime.now(clock)),
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
        val ATTENDANCE_VIEWABLE_STATUSES = setOf(RoomStatus.COMPLETED)
    }
}
