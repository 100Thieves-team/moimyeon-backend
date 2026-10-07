package io.plady.moimyeon.worker.notification

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.plady.moimyeon.core.enums.NotificationPolicy
import io.plady.moimyeon.storage.redis.NotificationStreamMessage
import io.plady.moimyeon.worker.notification.delivery.Notification
import io.plady.moimyeon.worker.notification.delivery.NotificationContent
import io.plady.moimyeon.worker.notification.delivery.NotificationSender
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class NotificationMessageHandler(
    private val jsonMapper: JsonMapper,
    private val notificationSender: NotificationSender,
    actionBaseUrl: String,
) {
    private val actionBaseUrl = actionBaseUrl.removeSuffix("/")

    fun handle(message: NotificationStreamMessage) {
        val payload = parse(message)
        if (message.channel !in payload.policy.channels) {
            throw InvalidNotificationMessageException(
                "알림 정책에 없는 채널입니다. eventType=${message.eventType}, policy=${payload.policy}, channel=${message.channel}",
            )
        }
        notificationSender.send(
            Notification(
                eventId = message.eventId,
                eventType = message.eventType,
                channel = message.channel,
                policy = payload.policy,
                recipientMemberId = payload.recipientMemberId,
                content = NotificationContent(
                    title = payload.title,
                    body = payload.body,
                    actionUrl = payload.actionPath?.let { "$actionBaseUrl/${it.removePrefix("/")}" },
                ),
            ),
        )
    }

    private fun parse(message: NotificationStreamMessage): NotificationPayload {
        val payload = try {
            jsonMapper.readValue(message.payload, NotificationPayload::class.java)
        } catch (exception: JacksonException) {
            throw InvalidNotificationMessageException("알림 payload를 해석할 수 없습니다.", exception)
        }
        if (payload.eventId != message.eventId) {
            throw InvalidNotificationMessageException(
                "Stream과 payload의 eventId가 일치하지 않습니다. stream=${message.eventId}, payload=${payload.eventId}",
            )
        }
        if (payload.title.isBlank()) {
            throw InvalidNotificationMessageException("알림 제목이 비어 있습니다. eventId=${message.eventId}")
        }
        return payload
    }
}

// API 가 필드를 더해도 worker 를 다시 배포하지 않아도 되게 한다.
@JsonIgnoreProperties(ignoreUnknown = true)
private data class NotificationPayload(
    val eventId: UUID,
    val policy: NotificationPolicy,
    val recipientMemberId: UUID,
    val title: String,
    val body: String,
    val actionPath: String?,
)
