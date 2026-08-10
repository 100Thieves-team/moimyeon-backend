package io.plady.moimyeon.core.notification

import io.plady.moimyeon.core.enums.EventType
import java.util.UUID

interface NotificationEvent {
    val eventId: UUID
    val eventType: EventType
}
