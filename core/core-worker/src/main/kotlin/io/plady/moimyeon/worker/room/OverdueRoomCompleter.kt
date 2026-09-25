package io.plady.moimyeon.worker.room

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.OutboxEntity
import io.plady.moimyeon.storage.db.core.OutboxRepository
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

// 예정 시각 +8시간이 지난 CONFIRMED 룸을 완료한다. 출석은 완료 이후 별도 API로 기록한다.
@Component
class OverdueRoomCompleter(
    private val roomRepository: RoomRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val participationRepository: ParticipationRepository,
    private val outboxRepository: OutboxRepository,
    private val jsonMapper: JsonMapper,
) {
    fun findOverdueRoomIds(now: LocalDateTime): List<UUID> = roomRepository
        .findConfirmedStartedBefore(now - COMPLETION_TIMEOUT, PageRequest.of(0, COMPLETION_BATCH_SIZE))
        .map { it.id }

    // 후보 조회 뒤 수동 완료나 방장 이탈이 끼어들 수 있으므로 룸 행을 잠그고 다시 판정한다.
    @Transactional
    fun complete(roomId: UUID, now: LocalDateTime): Boolean {
        val room = roomRepository.findByIdForUpdate(roomId)?.takeIf { it.isActive() } ?: return false
        if (!room.isAutoCompletable(now)) return false

        val recipientIds = participationRepository.findAllAtRoomConfirmation(roomId).map { it.memberId }
        room.complete()
        roomStatusLogRepository.save(
            RoomStatusLogEntity.bySystem(
                roomId = roomId,
                transitionType = RoomStatus.COMPLETED,
                occurredAt = now,
            ),
        )
        recipientIds.forEach { recipientMemberId -> saveCompletionOutbox(roomId, recipientMemberId) }
        return true
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun saveCompletionOutbox(roomId: UUID, recipientMemberId: UUID) {
        val eventId = Uuid.generateV7().toJavaUuid()
        val event = RoomCompletionNotificationEvent(
            eventId = eventId,
            eventType = EventType.ROOM_COMPLETED,
            roomId = roomId,
            recipientMemberId = recipientMemberId,
        )
        outboxRepository.save(
            OutboxEntity(
                id = eventId,
                eventType = event.eventType,
                payload = jsonMapper.writeValueAsString(event),
            ),
        )
    }

    companion object {
        const val COMPLETION_BATCH_SIZE: Int = 100
        val COMPLETION_TIMEOUT: Duration = Duration.ofHours(8)
    }
}

private data class RoomCompletionNotificationEvent(
    val eventId: UUID,
    val eventType: EventType,
    val roomId: UUID,
    val recipientMemberId: UUID,
)
