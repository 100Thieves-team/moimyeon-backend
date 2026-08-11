package io.plady.moimyeon.core.notification.outbox

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PendingOutboxRelayScheduler(
    private val outboxClaimManager: OutboxClaimManager,
    private val notificationRelay: NotificationRelay,
    private val relayCoordinator: OutboxRelayCoordinator,
) {
    @Scheduled(
        fixedDelayString = "\${notification.outbox.relay.fixed-delay:10s}",
        initialDelayString = "\${notification.outbox.relay.initial-delay:5s}",
    )
    fun relayPendingOutboxes() {
        relayCoordinator.relayPendingIfAvailable(::relayPendingBatch)
    }

    private fun relayPendingBatch() {
        outboxClaimManager.claimPendingBatch().forEach(notificationRelay::publish)
    }
}
