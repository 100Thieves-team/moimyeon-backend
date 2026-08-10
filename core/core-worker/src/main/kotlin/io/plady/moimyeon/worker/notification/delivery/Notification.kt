package io.plady.moimyeon.worker.notification.delivery

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
import java.util.UUID

data class Notification(
    val eventId: UUID,
    val eventType: EventType,
    val channel: NotificationChannel,
    val recipientMemberId: UUID,
    val content: NotificationContent,
)

data class NotificationContent(
    val title: String,
    val body: String,
    val actionPath: String?,
)
