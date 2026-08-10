package io.plady.moimyeon.worker.notification

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
import io.plady.moimyeon.storage.redis.NotificationStreamConsumer
import io.plady.moimyeon.storage.redis.NotificationStreamHandlingResult
import io.plady.moimyeon.storage.redis.NotificationStreamMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class NotificationMessageWorkerTest {
    @Test
    fun `등록된 알림 처리기로 Pending과 새 메시지를 전달한다`() {
        val firstEventId = eventId(31)
        val secondEventId = eventId(32)
        val message = message(firstEventId)
        val consumer = RecordingNotificationStreamConsumer(
            pendingMessages = listOf(message),
            newMessages = listOf(message.copy(eventId = secondEventId)),
        )
        val handled = mutableListOf<UUID>()
        val handler = mockk<NotificationMessageHandler>()
        every { handler.handle(any()) } answers { handled += firstArg<NotificationStreamMessage>().eventId }
        val worker = NotificationMessageWorker(
            messageConsumer = consumer,
            messageHandler = handler,
        )

        worker.consumeMessages()

        assertThat(handled).containsExactly(firstEventId, secondEventId)
        assertThat(consumer.recoverPendingCalled).isTrue()
        assertThat(consumer.consumeNewCalled).isTrue()
    }

    @Test
    fun `영구 처리 오류를 영구 실패 결과로 변환한다`() {
        val message = message(eventId(33))
        val consumer = RecordingNotificationStreamConsumer(newMessages = listOf(message))
        val handler = mockk<NotificationMessageHandler>()
        every { handler.handle(message) } throws PermanentNotificationProcessingException("잘못된 메시지")

        NotificationMessageWorker(consumer, handler).consumeMessages()

        assertThat(consumer.results.single().isPermanentFailure).isTrue()
    }

    @Test
    fun `일시 처리 오류를 재시도 실패 결과로 변환한다`() {
        val message = message(eventId(34))
        val consumer = RecordingNotificationStreamConsumer(newMessages = listOf(message))
        val handler = mockk<NotificationMessageHandler>()
        every { handler.handle(message) } throws RetryableNotificationProcessingException("일시 장애")

        NotificationMessageWorker(consumer, handler).consumeMessages()

        assertThat(consumer.results.single().isRetryableFailure).isTrue()
    }

    private fun message(eventId: UUID) = NotificationStreamMessage(
        eventId = eventId,
        eventType = EventType.ROOM_APPLICATION_ACCEPTED,
        channel = NotificationChannel.WEB_PUSH,
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
    val results = mutableListOf<NotificationStreamHandlingResult>()

    override fun recoverPending(handler: (NotificationStreamMessage) -> NotificationStreamHandlingResult): Int {
        recoverPendingCalled = true
        pendingMessages.mapTo(results, handler)
        return pendingMessages.size
    }

    override fun consumeNew(handler: (NotificationStreamMessage) -> NotificationStreamHandlingResult): Int {
        consumeNewCalled = true
        newMessages.mapTo(results, handler)
        return newMessages.size
    }
}
