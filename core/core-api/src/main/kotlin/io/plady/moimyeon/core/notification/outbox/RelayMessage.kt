package io.plady.moimyeon.core.notification.outbox

import io.plady.moimyeon.core.enums.EventType
import java.util.UUID

data class RelayMessage(
    val eventId: UUID,
    val eventType: EventType,
    val payload: String,
)
