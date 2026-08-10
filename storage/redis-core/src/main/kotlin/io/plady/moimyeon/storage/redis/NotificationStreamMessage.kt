package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
import java.util.UUID

data class NotificationStreamMessage(
    val eventId: UUID,
    val eventType: EventType,
    val channel: NotificationChannel,
    val payload: String,
)
