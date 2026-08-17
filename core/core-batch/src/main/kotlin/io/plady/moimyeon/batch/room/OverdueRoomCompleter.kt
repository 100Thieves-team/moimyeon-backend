package io.plady.moimyeon.batch.room

import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

// 진행 시작 +8시간이 지난 룸을 종료하는 안전장치(PRD 「룸 진행 마무리 및 출석」 §4.3, MOI-471).
// 1차 종료(출석자 전원 클로징 제출, MOI-469)가 일어나지 않은 룸을 시스템이 닫는다.
@Component
class OverdueRoomCompleter(
    private val roomRepository: RoomRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
) {
    fun findOverdueRoomIds(now: LocalDateTime): List<UUID> {
        return roomRepository.findInProgressStartedBefore(now - COMPLETION_TIMEOUT).map { it.id }
    }

    // 후보 조회와 이 트랜잭션 사이에 마지막 클로징 제출이 끼어들 수 있다 —
    // 제출 경로와 같은 룸 행 락을 잡고 상태를 재확인해야 전이가 정확히 한 번이 된다(PRD §4.5).
    @Transactional
    fun complete(roomId: UUID, now: LocalDateTime) {
        val room = roomRepository.findByIdForUpdate(roomId)?.takeIf { it.isActive() } ?: return
        if (room.status != RoomStatus.IN_PROGRESS) return

        room.complete()
        roomStatusLogRepository.save(
            RoomStatusLogEntity.bySystem(
                roomId = roomId,
                transitionType = RoomStatus.COMPLETED,
                occurredAt = now,
            ),
        )
    }

    companion object {
        val COMPLETION_TIMEOUT: Duration = Duration.ofHours(8)
    }
}
