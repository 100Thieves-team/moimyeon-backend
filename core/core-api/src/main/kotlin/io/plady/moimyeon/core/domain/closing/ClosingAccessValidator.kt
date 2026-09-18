package io.plady.moimyeon.core.domain.closing

import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ClosingAccessValidator(
    private val roomFinder: RoomFinder,
    private val roomProgressReader: RoomProgressReader,
) {
    fun validateParticipant(roomId: UUID, memberId: UUID) {
        requireBusiness(
            roomFinder.getRoom(roomId).status == RoomStatus.IN_PROGRESS,
            CoreErrorType.CLOSING_NOT_AVAILABLE,
        )
        requireBusiness(
            roomProgressReader.isAttended(roomId, memberId),
            CoreErrorType.CLOSING_SUBMISSION_FORBIDDEN,
        )
    }
}
