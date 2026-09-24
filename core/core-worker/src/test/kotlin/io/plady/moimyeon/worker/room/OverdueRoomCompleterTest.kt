package io.plady.moimyeon.worker.room

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.OutboxEntity
import io.plady.moimyeon.storage.db.core.OutboxRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.UUID

class OverdueRoomCompleterTest {
    private val roomRepository = mockk<RoomRepository>()
    private val logRepository = mockk<RoomStatusLogRepository>()
    private val participationRepository = mockk<ParticipationRepository>()
    private val outboxRepository = mockk<OutboxRepository>()
    private val jsonMapper = mockk<JsonMapper>()
    private val completer = OverdueRoomCompleter(
        roomRepository,
        logRepository,
        participationRepository,
        outboxRepository,
        jsonMapper,
    )
    private val roomId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 9, 24, 12, 0)

    @Test
    fun `후보 조회 기준은 예정 시각 8시간 경과다`() {
        val threshold = slot<LocalDateTime>()
        val pageable = slot<Pageable>()
        every { roomRepository.findConfirmedStartedBefore(capture(threshold), capture(pageable)) } returns emptyList()

        completer.findOverdueRoomIds(now)

        assertThat(threshold.captured).isEqualTo(now.minusHours(8))
        assertThat(pageable.captured.pageSize).isEqualTo(OverdueRoomCompleter.COMPLETION_BATCH_SIZE)
    }

    @Test
    fun `8시간 지난 확정 룸을 완료하고 전원 알림 Outbox를 같은 처리에서 저장한다`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val room = mockk<RoomEntity> {
            every { isActive() } returns true
            every { isAutoCompletable(now) } returns true
            every { complete() } just runs
        }
        every { roomRepository.findByIdForUpdate(roomId) } returns room
        every { participationRepository.findAllAtRoomConfirmation(roomId) } returns listOf(
            mockk<ParticipationEntity> { every { memberId } returns first },
            mockk<ParticipationEntity> { every { memberId } returns second },
        )
        every { logRepository.save(any()) } answers { firstArg() }
        every { jsonMapper.writeValueAsString(any<Any>()) } returns "{}"
        every { outboxRepository.save(any()) } answers { firstArg() }

        assertThat(completer.complete(roomId, now)).isTrue()

        verify(exactly = 1) { room.complete() }
        val logs = mutableListOf<RoomStatusLogEntity>()
        verify(exactly = 1) { logRepository.save(capture(logs)) }
        assertThat(logs.single().transitionType).isEqualTo(RoomStatus.COMPLETED)
        val outboxes = mutableListOf<OutboxEntity>()
        verify(exactly = 2) { outboxRepository.save(capture(outboxes)) }
        assertThat(outboxes).allSatisfy { assertThat(it.eventType).isEqualTo(EventType.ROOM_COMPLETED) }
    }

    @Test
    fun `락 뒤 자동 완료 조건이 아니면 건너뛴다`() {
        val room = mockk<RoomEntity> {
            every { isActive() } returns true
            every { isAutoCompletable(now) } returns false
        }
        every { roomRepository.findByIdForUpdate(roomId) } returns room

        assertThat(completer.complete(roomId, now)).isFalse()

        verify(exactly = 0) { logRepository.save(any()) }
    }
}
