package io.plady.moimyeon.batch.room

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RoomAutoCompleteJob(
    private val overdueRoomCompleter: OverdueRoomCompleter,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    // 8시간 지연은 후기·참여 슬롯 복구가 걸린 사용자 대기 시간이라 주기를 짧게 둔다.
    // 룸마다 트랜잭션을 따로 열어 한 룸의 실패가 나머지 전이를 막지 않게 한다.
    @Scheduled(cron = "0 */10 * * * *")
    fun run() {
        val now = LocalDateTime.now()
        val overdueRoomIds = overdueRoomCompleter.findOverdueRoomIds(now)
        if (overdueRoomIds.isEmpty()) return

        var completed = 0
        overdueRoomIds.forEach { roomId ->
            runCatching { overdueRoomCompleter.complete(roomId, now) }
                .onSuccess { completed++ }
                .onFailure { e -> log.error("룸 자동 종료 실패 roomId={}", roomId, e) }
        }
        log.info("룸 자동 종료 완료 {}/{}", completed, overdueRoomIds.size)
    }
}
