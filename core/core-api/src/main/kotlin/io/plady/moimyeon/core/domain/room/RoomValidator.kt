package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

@Component
class RoomValidator(
    private val roomRepository: RoomRepository,
) {
    fun validateAcceptingApplications(roomId: UUID, now: LocalDateTime) {
        val room = roomRepository.findByIdForUpdate(roomId)
        if (room == null || !room.isActive()) {
            throw CoreException(CoreErrorType.ROOM_NOT_FOUND)
        }
        requireBusiness(room.status == RoomStatus.RECRUITING, CoreErrorType.ROOM_NOT_RECRUITING)
        requireBusiness(room.startAt.isAfter(now), CoreErrorType.ROOM_APPLICATION_SCHEDULE_PASSED)
    }
}
