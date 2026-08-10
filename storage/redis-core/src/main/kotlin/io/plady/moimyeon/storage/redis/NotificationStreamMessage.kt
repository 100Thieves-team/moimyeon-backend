package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.core.enums.EventType
import java.util.UUID

data class NotificationStreamMessage(
    val eventId: UUID,
    val eventType: EventType,
    val payload: String,
)
