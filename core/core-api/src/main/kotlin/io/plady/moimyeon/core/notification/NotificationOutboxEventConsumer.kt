package io.plady.moimyeon.core.notification

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationPolicy
import io.plady.moimyeon.core.event.OutboxEvent
import io.plady.moimyeon.core.event.OutboxEventConsumer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@Component
class NotificationOutboxEventConsumer(
    private val notificationComposer: NotificationComposer,
    private val jsonMapper: JsonMapper,
    private val messagePublisherProvider: ObjectProvider<NotificationMessagePublisher>,
) : OutboxEventConsumer {
    override fun consume(event: OutboxEvent) {
        val messages = notificationComposer.compose(event)
        if (messages.isEmpty()) return

        val messagePublisher = checkNotNull(messagePublisherProvider.ifAvailable) {
            "알림 메시지 발행 구현이 없습니다. eventId=${event.eventId}"
        }
        messagePublisher.publish(
            messages.map { message ->
                OutgoingNotification(
                    eventId = event.eventId,
                    eventType = event.type,
                    policy = message.policy,
                    payload = jsonMapper.writeValueAsString(NotificationPayload.of(event, message)),
                )
            },
        )
    }
}

// worker 가 읽는 공통 형식이다. 업무 필드를 실으면 worker 가 이벤트 종류에 묶인다.
private data class NotificationPayload(
    val eventId: UUID,
    val eventType: EventType,
    val policy: NotificationPolicy,
    val recipientMemberId: UUID,
    val title: String,
    val body: String,
    val actionPath: String?,
) {
    companion object {
        fun of(event: OutboxEvent, message: ComposedNotification) = NotificationPayload(
            eventId = event.eventId,
            eventType = event.type,
            policy = message.policy,
            recipientMemberId = message.recipientMemberId,
            title = message.title,
            body = message.body,
            actionPath = message.actionPath,
        )
    }
}
