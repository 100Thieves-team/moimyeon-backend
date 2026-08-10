package io.plady.moimyeon.core.notification.outbox

import io.plady.moimyeon.core.enums.EventType
import java.util.UUID

internal data class OutboxClaim(
    val eventId: UUID,
    val eventType: EventType,
    val payload: String,
    val claimToken: String,
) {
    fun relayMessage() = RelayMessage(
        eventId = eventId,
        eventType = eventType,
        payload = payload,
    )
}
