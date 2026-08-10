package io.plady.moimyeon.worker.notification

import io.plady.moimyeon.core.enums.EventType
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
) {
    fun handle(message: NotificationStreamMessage) {
        if (message.channel !in message.eventType.notificationChannels) {
            throw InvalidNotificationMessageException(
                "이벤트 정책에 없는 알림 채널입니다. eventType=${message.eventType}, channel=${message.channel}",
            )
        }
        val notification = when (message.eventType) {
            EventType.ROOM_APPLICATION_ACCEPTED -> roomApplicationAccepted(message)
        }
        notificationSender.send(notification)
    }

    private fun roomApplicationAccepted(message: NotificationStreamMessage): Notification {
        val payload = try {
            jsonMapper.readValue(
                message.payload,
                RoomApplicationAcceptedPayload::class.java,
            )
        } catch (exception: JacksonException) {
            throw InvalidNotificationMessageException("알림 payload를 해석할 수 없습니다.", exception)
        }
        if (payload.eventId != message.eventId) {
            throw InvalidNotificationMessageException(
                "Stream과 payload의 eventId가 일치하지 않습니다. stream=${message.eventId}, payload=${payload.eventId}",
            )
        }
        if (payload.eventType != message.eventType) {
            throw InvalidNotificationMessageException(
                "Stream과 payload의 eventType이 일치하지 않습니다. stream=${message.eventType}, payload=${payload.eventType}",
            )
        }
        return Notification(
            eventId = message.eventId,
            eventType = message.eventType,
            channel = message.channel,
            recipientMemberId = payload.applicantMemberId,
            content = NotificationContent(
                title = "참가 신청이 수락되었어요",
                body = "모임에 참여할 수 있게 되었어요.",
                actionPath = "/rooms/${payload.roomId}",
            ),
        )
    }
}

private data class RoomApplicationAcceptedPayload(
    val eventId: UUID,
    val eventType: EventType,
    val applicationId: Long,
    val roomId: UUID,
    val applicantMemberId: UUID,
)
