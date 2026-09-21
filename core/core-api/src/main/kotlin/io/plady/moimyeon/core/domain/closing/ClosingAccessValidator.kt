package io.plady.moimyeon.core.domain.closing

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class ClosingAccessValidator(
    private val roomFinder: RoomFinder,
    private val roomProgressReader: RoomProgressReader,
) {
    fun validateParticipant(roomId: UUID, memberId: UUID) {
        log.debug { "closing-access.validator.validateParticipant roomId=$roomId memberId=$memberId" }
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
