package io.plady.moimyeon.core.notification

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationPolicy
import java.util.UUID

data class OutgoingNotification(
    val eventId: UUID,
    val eventType: EventType,
    val policy: NotificationPolicy,
    val payload: String,
)
