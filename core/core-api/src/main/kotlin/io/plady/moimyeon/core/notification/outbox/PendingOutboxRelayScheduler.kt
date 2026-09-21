package io.plady.moimyeon.core.notification.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

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
        val claims = outboxClaimManager.claimPendingBatch()
        if (claims.isNotEmpty()) log.debug { "outbox.relay.batch size=${claims.size}" }
        claims.forEach(notificationRelay::publish)
    }
}
