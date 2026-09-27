package io.plady.moimyeon.worker.notification.delivery

import io.plady.moimyeon.core.enums.NotificationChannel
import io.plady.moimyeon.core.enums.NotificationPolicy
import java.util.UUID

data class Notification(
    val eventId: UUID,
    val eventType: String,
    val channel: NotificationChannel,
    val policy: NotificationPolicy,
    val recipientMemberId: UUID,
    val content: NotificationContent,
)

data class NotificationContent(
    val title: String,
    val body: String,
    val actionUrl: String?,
)
