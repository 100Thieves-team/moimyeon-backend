package io.plady.moimyeon.core.notification.outbox

fun interface OutboxRelayCoordinator {
    fun relayPendingIfAvailable(relay: () -> Unit): Boolean
}
