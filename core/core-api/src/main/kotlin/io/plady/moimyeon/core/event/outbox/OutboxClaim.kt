package io.plady.moimyeon.core.event.outbox

import java.util.UUID

internal data class OutboxClaim(
    val eventId: UUID,
    val payload: String,
    val claimToken: String,
)
