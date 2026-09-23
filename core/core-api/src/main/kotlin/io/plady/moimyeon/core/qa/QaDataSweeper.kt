package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

// 여러 커밋을 조립하는 도구라 트랜잭션을 갖지 않는다. 룸·회원 하나가 한 트랜잭션(각 Eraser 의 erase)이고,
// 하나가 실패해도 앞선 것은 지워진 채 남는다. 목록 조회와 삭제 사이에 먼저 지워진 것은 이미 목표 상태라 건너뛴다.
@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaDataSweeper(
    private val qaRoomFinder: QaRoomFinder,
    private val qaMemberFinder: QaMemberFinder,
    private val qaRoomEraser: QaRoomEraser,
    private val qaMemberEraser: QaMemberEraser,
) {
    fun sweepRooms(condition: QaDataCondition): QaDeletedRows {
        log.debug { "qa-data.sweeper.sweepRooms narrowed=${condition.isNarrowed()} hostMemberId=${condition.hostMemberId}" }
        return qaRoomFinder.getRoomIds(condition).fold(QaDeletedRows.NONE) { acc, roomId ->
            acc + runCatching { qaRoomEraser.erase(roomId) }
                .recoverCatching { it.alreadyGoneOr(CoreErrorType.ROOM_NOT_FOUND) }
                .onFailure { log.warn(it) { "qa-data.sweeper.sweepRooms.failed roomId=$roomId ${acc.toLogValues()}" } }
                .getOrThrow()
        }
    }

    fun sweepMembers(): QaDeletedRows {
        log.debug { "qa-data.sweeper.sweepMembers" }
        return qaMemberFinder.getQaMembers().fold(QaDeletedRows.NONE) { acc, member ->
            acc + runCatching { qaMemberEraser.erase(member.id) }
                .recoverCatching { it.alreadyGoneOr(CoreErrorType.MEMBER_NOT_FOUND) }
                .onFailure { log.warn(it) { "qa-data.sweeper.sweepMembers.failed memberId=${member.id} ${acc.toLogValues()}" } }
                .getOrThrow()
        }
    }

    private fun Throwable.alreadyGoneOr(notFound: CoreErrorType): QaDeletedRows = if (this is CoreException && errorType == notFound) QaDeletedRows.NONE else throw this
}
