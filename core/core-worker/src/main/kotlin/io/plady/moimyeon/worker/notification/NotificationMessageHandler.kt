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
            EventType.ROOM_CONFIRMED,
            EventType.ROOM_COMPLETED,
            EventType.ROOM_CANCELED,
            EventType.ROOM_REVIEW_REQUESTED,
            -> roomLifecycle(message)
        }
        notificationSender.send(notification)
    }

    private fun roomLifecycle(message: NotificationStreamMessage): Notification {
        val payload = decode(message, RoomLifecyclePayload::class.java)
        validateEnvelope(message, payload.eventId, payload.eventType)
        val content = when (message.eventType) {
            EventType.ROOM_CONFIRMED -> NotificationContent(
                title = "모임 진행이 확정되었어요",
                body = "확정된 일정과 참여자를 확인해 주세요.",
                actionPath = "/rooms/${payload.roomId}",
            )
            EventType.ROOM_COMPLETED -> NotificationContent(
                title = "모임이 완료되었어요",
                body = "참석 결과가 기록되었어요.",
                actionPath = "/rooms/${payload.roomId}",
            )
            EventType.ROOM_CANCELED -> NotificationContent(
                title = "모임이 취소되었어요",
                body = "참여 중이던 모임의 취소 내용을 확인해 주세요.",
                actionPath = "/rooms/${payload.roomId}",
            )
            EventType.ROOM_REVIEW_REQUESTED -> NotificationContent(
                title = "함께한 참여자의 후기를 남겨 주세요",
                body = "완료된 모임의 리뷰를 작성할 수 있어요.",
                actionPath = "/rooms/${payload.roomId}",
            )
            EventType.ROOM_APPLICATION_ACCEPTED -> error("지원 수락 이벤트는 별도 처리합니다.")
        }
        return Notification(
            eventId = message.eventId,
            eventType = message.eventType,
            channel = message.channel,
            recipientMemberId = payload.recipientMemberId,
            content = content,
        )
    }

    private fun <T> decode(message: NotificationStreamMessage, payloadType: Class<T>): T = try {
        jsonMapper.readValue(message.payload, payloadType)
    } catch (exception: JacksonException) {
        throw InvalidNotificationMessageException("알림 payload를 해석할 수 없습니다.", exception)
    }

    private fun validateEnvelope(message: NotificationStreamMessage, eventId: UUID, eventType: EventType) {
        if (eventId != message.eventId) {
            throw InvalidNotificationMessageException(
                "Stream과 payload의 eventId가 일치하지 않습니다. stream=${message.eventId}, payload=$eventId",
            )
        }
        if (eventType != message.eventType) {
            throw InvalidNotificationMessageException(
                "Stream과 payload의 eventType이 일치하지 않습니다. stream=${message.eventType}, payload=$eventType",
            )
        }
    }

    private fun roomApplicationAccepted(message: NotificationStreamMessage): Notification {
        val payload = decode(message, RoomApplicationAcceptedPayload::class.java)
        validateEnvelope(message, payload.eventId, payload.eventType)
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

private data class RoomLifecyclePayload(
    val eventId: UUID,
    val eventType: EventType,
    val roomId: UUID,
    val recipientMemberId: UUID,
)
