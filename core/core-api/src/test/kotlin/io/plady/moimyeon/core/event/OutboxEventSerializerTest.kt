package io.plady.moimyeon.core.event

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.event.payload.EventPayload
import io.plady.moimyeon.core.event.payload.ReviewPublishedEventPayload
import io.plady.moimyeon.core.event.payload.RoomApplicationAcceptedEventPayload
import io.plady.moimyeon.core.event.payload.RoomApplicationRejectedEventPayload
import io.plady.moimyeon.core.event.payload.RoomApplicationSubmittedEventPayload
import io.plady.moimyeon.core.event.payload.RoomCanceledEventPayload
import io.plady.moimyeon.core.event.payload.RoomCommentPostedEventPayload
import io.plady.moimyeon.core.event.payload.RoomCompletedEventPayload
import io.plady.moimyeon.core.event.payload.RoomConfirmedEventPayload
import io.plady.moimyeon.core.event.payload.RoomHostDelegatedEventPayload
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.util.UUID

class OutboxEventSerializerTest {
    private val serializer = OutboxEventSerializer(JsonMapper.builder().addModule(kotlinModule()).build())

    @Test
    fun `모든 이벤트 종류가 저장한 모양 그대로 되읽힌다`() {
        val events = samplePayloads().map { (type, payload) -> OutboxEvent(EventIdGenerator.generate(), type, payload) }

        assertThat(events.map { it.type }).containsExactlyInAnyOrder(*EventType.entries.toTypedArray())
        events.forEach { event ->
            assertThat(serializer.deserialize(serializer.serialize(event))).isEqualTo(event)
        }
    }

    @Test
    fun `payload 에 모르는 필드가 있어도 읽는다`() {
        val event = OutboxEvent(
            EventIdGenerator.generate(),
            EventType.ROOM_CANCELED,
            RoomCanceledEventPayload(UUID.randomUUID(), "룸", UUID.randomUUID(), emptyList(), emptyList()),
        )
        val withExtraField = serializer.serialize(event).replace("\"roomTitle\"", "\"futureField\":1,\"roomTitle\"")

        assertThat(serializer.deserialize(withExtraField)).isEqualTo(event)
    }

    @Test
    fun `모르는 이벤트 종류는 따로 구분되는 예외로 알린다`() {
        val json = """{"eventId":"${UUID.randomUUID()}","type":"SOME_FUTURE_EVENT","payload":{}}"""

        assertThatThrownBy { serializer.deserialize(json) }.isInstanceOf(UnknownOutboxEventTypeException::class.java)
    }

    private fun samplePayloads(): List<Pair<EventType, EventPayload>> {
        val roomId = UUID.randomUUID()
        val memberA = UUID.randomUUID()
        val memberB = UUID.randomUUID()
        return listOf(
            EventType.ROOM_APPLICATION_SUBMITTED to RoomApplicationSubmittedEventPayload(1L, roomId, "룸", memberA, memberB),
            EventType.ROOM_APPLICATION_ACCEPTED to RoomApplicationAcceptedEventPayload(1L, roomId, "룸", memberA),
            EventType.ROOM_APPLICATION_REJECTED to RoomApplicationRejectedEventPayload(1L, roomId, "룸", memberA),
            EventType.ROOM_CONFIRMED to RoomConfirmedEventPayload(roomId, "룸", memberA, listOf(memberA, memberB), listOf(memberB)),
            EventType.ROOM_COMPLETED to RoomCompletedEventPayload(roomId, "룸", null, listOf(memberA, memberB), listOf(memberA)),
            EventType.ROOM_CANCELED to RoomCanceledEventPayload(roomId, "룸", memberA, listOf(memberA), listOf(memberB)),
            EventType.ROOM_HOST_DELEGATED to RoomHostDelegatedEventPayload(roomId, "룸", memberA, memberB, listOf(memberB)),
            EventType.REVIEW_PUBLISHED to ReviewPublishedEventPayload(1L, roomId, "룸", memberA, memberB),
            EventType.ROOM_COMMENT_POSTED to RoomCommentPostedEventPayload(1L, roomId, "룸", memberA, listOf(memberA, memberB)),
        )
    }
}
