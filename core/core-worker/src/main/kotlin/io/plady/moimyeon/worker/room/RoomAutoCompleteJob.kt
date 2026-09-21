package io.plady.moimyeon.worker.room

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@ConditionalOnProperty(prefix = "room.auto-complete", name = ["enabled"], havingValue = "true")
@Component
class RoomAutoCompleteJob(
    private val overdueRoomCompleter: OverdueRoomCompleter,
    private val clock: Clock,
) {

    // 8시간 지연은 후기·참여 슬롯 복구가 걸린 사용자 대기 시간이라 주기를 짧게 둔다.
    // 룸마다 트랜잭션을 따로 열어 한 룸의 실패가 나머지 전이를 막지 않게 한다.
    @Scheduled(cron = "\${room.auto-complete.cron:0 */10 * * * *}")
    fun run() {
        val now = LocalDateTime.now(clock)
        val overdueRoomIds = overdueRoomCompleter.findOverdueRoomIds(now)
        if (overdueRoomIds.isEmpty()) return

        var completed = 0
        overdueRoomIds.forEach { roomId ->
            runCatching { overdueRoomCompleter.complete(roomId, now) }
                .onSuccess { if (it) completed++ }
                .onFailure { exception -> log.error(exception) { "room.auto-complete.failed roomId=$roomId" } }
        }
        log.info { "room.auto-complete.completed completed=$completed total=${overdueRoomIds.size}" }
    }
}
