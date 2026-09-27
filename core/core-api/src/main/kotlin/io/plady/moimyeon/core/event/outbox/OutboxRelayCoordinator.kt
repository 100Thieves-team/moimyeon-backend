package io.plady.moimyeon.core.event.outbox

fun interface OutboxRelayCoordinator {
    fun relayPendingIfAvailable(relay: () -> Unit): Boolean
}
