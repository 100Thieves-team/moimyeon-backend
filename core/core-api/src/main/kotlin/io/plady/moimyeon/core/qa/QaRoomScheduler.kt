package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.qa.QaTestDataRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaRoomScheduler(
    private val qaTestDataRepository: QaTestDataRepository,
) {
    @Transactional
    fun reschedule(roomId: UUID, startAt: LocalDateTime): QaRoomSchedule {
        log.debug { "qa-room.scheduler.reschedule roomId=$roomId startAt=$startAt" }
        val room = requireFound(qaTestDataRepository.findRoom(roomId), CoreErrorType.ROOM_NOT_FOUND)
        requireBusiness(QaDataCondition.isQaData(room.title), CoreErrorType.QA_DATA_ONLY)
        qaTestDataRepository.updateRoomStartAt(roomId, startAt)
        return QaRoomSchedule(roomId = roomId, status = room.status, startAt = startAt)
    }
}
