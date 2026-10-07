package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.domain.progress.RoomProgressManager
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.qa.QaTestDataRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * 자동 완료 작업(RoomAutoCompleteJob)이 룸 하나에 하는 일을 지금 한다. 10분 주기를 기다리지 않으려는 것이다.
 * 자동 완료 조건(확정 상태, 시작 뒤 일정 시간 지남)은 그대로 판정하므로 조건이 안 되면 완료하지 않는다.
 */
@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaRoomAutoCompleter(
    private val qaTestDataRepository: QaTestDataRepository,
    private val roomProgressManager: RoomProgressManager,
    private val clock: Clock,
) {
    fun complete(roomId: UUID): QaRoomAutoCompletion {
        log.debug { "qa-room.auto-completer.complete roomId=$roomId" }
        val room = requireFound(qaTestDataRepository.findRoom(roomId), CoreErrorType.ROOM_NOT_FOUND)
        requireBusiness(QaDataCondition.isQaData(room.title), CoreErrorType.QA_DATA_ONLY)
        val completed = roomProgressManager.completeOverdue(roomId, LocalDateTime.now(clock))
        // 완료는 잠근 뒤 다시 판정한다. 그사이 자동 완료 작업이 먼저 끝냈을 수 있으니 상태는 다시 읽는다
        val status = qaTestDataRepository.findRoom(roomId)?.status ?: room.status
        return QaRoomAutoCompletion(roomId = roomId, completed = completed, status = status)
    }
}
