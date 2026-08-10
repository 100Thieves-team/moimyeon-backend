package io.plady.moimyeon.worker.notification

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.storage.redis.NotificationStreamConsumer
import io.plady.moimyeon.storage.redis.NotificationStreamMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class NotificationMessageWorkerTest {
    @Test
    fun `등록된 이벤트 처리기로 Pending과 새 메시지를 전달한다`() {
        val firstEventId = eventId(31)
        val secondEventId = eventId(32)
        val message = message(firstEventId)
        val consumer = RecordingNotificationStreamConsumer(
            pendingMessages = listOf(message),
            newMessages = listOf(message.copy(eventId = secondEventId)),
        )
        val handled = mutableListOf<UUID>()
        val worker = NotificationMessageWorker(
            messageConsumer = consumer,
            handlers = listOf(
                handler(EventType.ROOM_APPLICATION_ACCEPTED) { handled += it.eventId },
            ),
        )

        worker.consumeMessages()

        assertThat(handled).containsExactly(firstEventId, secondEventId)
        assertThat(consumer.recoverPendingCalled).isTrue()
        assertThat(consumer.consumeNewCalled).isTrue()
    }

    @Test
    fun `등록된 이벤트 처리기가 없으면 Stream 메시지를 가져오지 않는다`() {
        val consumer = RecordingNotificationStreamConsumer()
        val worker = NotificationMessageWorker(
            messageConsumer = consumer,
            handlers = emptyList(),
        )

        worker.consumeMessages()

        assertThat(consumer.recoverPendingCalled).isFalse()
        assertThat(consumer.consumeNewCalled).isFalse()
    }

    private fun handler(
        eventType: EventType,
        handle: (NotificationStreamMessage) -> Unit,
    ) = object : NotificationMessageHandler {
        override val eventType: EventType = eventType

        override fun handle(message: NotificationStreamMessage) = handle(message)
    }

    private fun message(eventId: UUID) = NotificationStreamMessage(
        eventId = eventId,
        eventType = EventType.ROOM_APPLICATION_ACCEPTED,
        payload = "{\"eventId\":\"$eventId\"}",
    )

    private fun eventId(value: Long): UUID = UUID.fromString(
        "0198b4f4-2f00-7000-8000-${value.toString().padStart(12, '0')}",
    )
}

private class RecordingNotificationStreamConsumer(
    private val pendingMessages: List<NotificationStreamMessage> = emptyList(),
    private val newMessages: List<NotificationStreamMessage> = emptyList(),
) : NotificationStreamConsumer {
    var recoverPendingCalled: Boolean = false
        private set
    var consumeNewCalled: Boolean = false
        private set

    override fun recoverPending(handler: (NotificationStreamMessage) -> Unit): Int {
        recoverPendingCalled = true
        pendingMessages.forEach(handler)
        return pendingMessages.size
    }

    override fun consumeNew(handler: (NotificationStreamMessage) -> Unit): Int {
        consumeNewCalled = true
        newMessages.forEach(handler)
        return newMessages.size
    }
}
