package io.plady.moimyeon.core.event

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.event.payload.EventPayload
import java.util.UUID

data class OutboxEvent(
    val eventId: UUID,
    val type: EventType,
    val payload: EventPayload,
)
