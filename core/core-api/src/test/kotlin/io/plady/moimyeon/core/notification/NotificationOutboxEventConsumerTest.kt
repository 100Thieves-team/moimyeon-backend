package io.plady.moimyeon.core.notification

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationPolicy
import io.plady.moimyeon.core.event.OutboxEvent
import io.plady.moimyeon.core.event.payload.ReviewPublishedEventPayload
import io.plady.moimyeon.core.event.payload.RoomConfirmedEventPayload
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.util.UUID

class NotificationOutboxEventConsumerTest {
    private val jsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()
    private val messagePublisher = mockk<NotificationMessagePublisher>(relaxed = true)
    private val publisherProvider = mockk<ObjectProvider<NotificationMessagePublisher>> {
        every { ifAvailable } returns messagePublisher
    }
    private val consumer = NotificationOutboxEventConsumer(NotificationComposer(), jsonMapper, publisherProvider)

    @Test
    fun `한 사실에서 나온 알림을 한 번에 넘긴다`() {
        val published = slot<List<OutgoingNotification>>()
        every { messagePublisher.publish(capture(published)) } returns Unit

        consumer.consume(CONFIRMED)

        assertThat(published.captured.map { it.policy })
            .containsExactly(NotificationPolicy.PUSH_AND_EMAIL, NotificationPolicy.PUSH_AND_EMAIL, NotificationPolicy.PUSH_ELSE_EMAIL)
        assertThat(published.captured.map { it.eventId }).containsOnly(CONFIRMED.eventId)
        assertThat(published.captured.map { it.eventType }).containsOnly(EventType.ROOM_CONFIRMED)
    }

    @Test
    fun `worker 가 읽는 공통 형식에는 받는 사람과 문구만 싣고 사실의 업무 필드는 싣지 않는다`() {
        val published = slot<List<OutgoingNotification>>()
        every { messagePublisher.publish(capture(published)) } returns Unit

        consumer.consume(CONFIRMED)

        val payload = jsonMapper.readTree(published.captured.first().payload)
        assertThat(payload.propertyNames().toSet()).containsExactlyInAnyOrder(
            "eventId",
            "eventType",
            "policy",
            "recipientMemberId",
            "title",
            "body",
            "actionPath",
        )
        assertThat(payload["recipientMemberId"].asString()).isEqualTo(HOST.toString())
        assertThat(payload["actionPath"].asString()).isEqualTo("/rooms/$ROOM_ID")
    }

    @Test
    fun `알릴 사람이 없으면 아무것도 넘기지 않는다`() {
        consumer.consume(
            OutboxEvent(UUID.randomUUID(), EventType.REVIEW_PUBLISHED, ReviewPublishedEventPayload(1L, ROOM_ID, "룸", HOST, HOST)),
        )

        verify(exactly = 0) { messagePublisher.publish(any()) }
    }

    @Test
    fun `알림 발행 구현이 없으면 outbox 를 남기도록 실패한다`() {
        every { publisherProvider.ifAvailable } returns null

        assertThatThrownBy { consumer.consume(CONFIRMED) }.isInstanceOf(IllegalStateException::class.java)
    }

    private companion object {
        val ROOM_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val HOST: UUID = UUID.fromString("00000000-0000-0000-0000-00000000000a")
        val PARTICIPANT: UUID = UUID.fromString("00000000-0000-0000-0000-00000000000c")
        val APPLICANT: UUID = UUID.fromString("00000000-0000-0000-0000-00000000000b")
        val CONFIRMED = OutboxEvent(
            eventId = UUID.fromString("0198b4f4-2f00-7000-8000-000000000001"),
            type = EventType.ROOM_CONFIRMED,
            payload = RoomConfirmedEventPayload(ROOM_ID, "토스 백엔드 모의면접", HOST, listOf(HOST, PARTICIPANT), listOf(APPLICANT)),
        )
    }
}
