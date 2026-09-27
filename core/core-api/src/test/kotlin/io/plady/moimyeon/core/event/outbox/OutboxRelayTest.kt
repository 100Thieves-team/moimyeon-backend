package io.plady.moimyeon.core.event.outbox

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.event.OutboxEvent
import io.plady.moimyeon.core.event.OutboxEventConsumer
import io.plady.moimyeon.core.event.OutboxEventSerializer
import io.plady.moimyeon.core.event.payload.RoomApplicationAcceptedEventPayload
import io.plady.moimyeon.storage.db.core.OutboxEntity
import io.plady.moimyeon.storage.db.core.OutboxRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.util.UUID

class OutboxRelayTest {
    private val outboxRepository = mockk<OutboxRepository>()
    private val outboxClaimManager = mockk<OutboxClaimManager>(relaxed = true)
    private val consumed = mutableListOf<OutboxEvent>()
    private var failConsumer = false
    private val consumer = OutboxEventConsumer { event ->
        if (failConsumer) throw IllegalStateException("소비 실패")
        consumed += event
    }
    private val serializer = OutboxEventSerializer(JsonMapper.builder().addModule(kotlinModule()).build())
    private val relay = OutboxRelay(outboxRepository, serializer, outboxClaimManager, listOf(consumer))

    @Test
    fun `사실을 종류와 함께 outbox 에 저장한다`() {
        val saved = slot<OutboxEntity>()
        every { outboxRepository.save(capture(saved)) } answers { saved.captured }

        relay.record(EVENT)

        assertThat(saved.captured.id).isEqualTo(EVENT.eventId)
        assertThat(saved.captured.eventType).isEqualTo(EventType.ROOM_APPLICATION_ACCEPTED.name)
        assertThat(serializer.deserialize(saved.captured.payload)).isEqualTo(EVENT)
    }

    @Test
    fun `소비자가 모두 성공하면 outbox 를 지운다`() {
        val claim = claim(serializer.serialize(EVENT))

        relay.relay(claim)

        assertThat(consumed).containsExactly(EVENT)
        verify(exactly = 1) { outboxClaimManager.complete(claim) }
        verify(exactly = 0) { outboxClaimManager.release(any()) }
    }

    @Test
    fun `소비자가 실패하면 다시 시도할 수 있게 선점을 푼다`() {
        failConsumer = true
        val claim = claim(serializer.serialize(EVENT))

        relay.relay(claim)

        verify(exactly = 1) { outboxClaimManager.release(claim) }
        verify(exactly = 0) { outboxClaimManager.complete(any()) }
    }

    @Test
    fun `형식이 깨진 행은 소비자에게 넘기지 않고 읽을 수 없는 행으로 남긴다`() {
        val claim = claim("{\"applicationId\":1}")

        relay.relay(claim)

        assertThat(consumed).isEmpty()
        verify(exactly = 1) { outboxClaimManager.markUnreadable(claim) }
        verify(exactly = 0) { outboxClaimManager.complete(any()) }
    }

    @Test
    fun `모르는 이벤트 종류는 새 버전 서버가 처리하도록 선점을 푼다`() {
        val claim = claim(serializer.serialize(EVENT).replace("ROOM_APPLICATION_ACCEPTED", "SOME_FUTURE_EVENT"))

        relay.relay(claim)

        assertThat(consumed).isEmpty()
        verify(exactly = 1) { outboxClaimManager.release(claim) }
        verify(exactly = 0) { outboxClaimManager.markUnreadable(any()) }
    }

    @Test
    fun `소비자 하나가 실패하면 앞서 성공한 소비자까지 다음 시도에 같은 사실을 다시 받는다`() {
        val succeeded = mutableListOf<OutboxEvent>()
        val relay = OutboxRelay(
            outboxRepository,
            serializer,
            outboxClaimManager,
            listOf(OutboxEventConsumer { succeeded += it }, OutboxEventConsumer { throw IllegalStateException("소비 실패") }),
        )
        val claim = claim(serializer.serialize(EVENT))

        relay.relay(claim)
        relay.relay(claim)

        assertThat(succeeded).containsExactly(EVENT, EVENT)
        verify(exactly = 2) { outboxClaimManager.release(claim) }
    }

    @Test
    fun `소비 후 완료 기록이 실패해도 선점을 풀지 않는다`() {
        val claim = claim(serializer.serialize(EVENT))
        every { outboxClaimManager.complete(claim) } throws IllegalStateException("DB 완료 기록 실패")

        relay.relay(claim)

        verify(exactly = 0) { outboxClaimManager.release(any()) }
    }

    private fun claim(payload: String) = OutboxClaim(eventId = EVENT.eventId, payload = payload, claimToken = "claim-token")

    private companion object {
        val EVENT = OutboxEvent(
            eventId = UUID.fromString("0198b4f4-2f00-7000-8000-000000000001"),
            type = EventType.ROOM_APPLICATION_ACCEPTED,
            payload = RoomApplicationAcceptedEventPayload(
                applicationId = 1L,
                roomId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                roomTitle = "토스 백엔드 모의면접",
                applicantMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            ),
        )
    }
}
