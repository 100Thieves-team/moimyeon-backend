package io.plady.moimyeon.core.notification.outbox

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Profile("test")
@Component
internal class DirectOutboxRelayCoordinator : OutboxRelayCoordinator {
    override fun relayPendingIfAvailable(relay: () -> Unit): Boolean {
        relay()
        return true
    }
}
