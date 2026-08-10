package io.plady.moimyeon.core.notification.outbox

import io.plady.moimyeon.storage.db.core.OutboxEntity
import io.plady.moimyeon.storage.db.core.OutboxRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class OutboxClaimManager(
    private val outboxRepository: OutboxRepository,
    private val properties: PendingOutboxRelayProperties,
    private val clock: Clock,
) {
    @Transactional
    internal fun claim(eventId: UUID): OutboxClaim? {
        val now = LocalDateTime.now(clock)
        val outbox = outboxRepository.findClaimableByIdForUpdate(eventId, now) ?: return null
        return claim(outbox, now)
    }

    @Transactional
    internal fun claimPendingBatch(): List<OutboxClaim> {
        val now = LocalDateTime.now(clock)
        return outboxRepository.findClaimableBatchForUpdate(
            createdBefore = now.minus(properties.staleAfter),
            now = now,
            batchSize = properties.batchSize,
        ).map { claim(it, now) }
    }

    @Transactional
    internal fun complete(claim: OutboxClaim): Boolean {
        return outboxRepository.deleteClaimed(claim.eventId, claim.claimToken) == 1
    }

    @Transactional
    internal fun release(claim: OutboxClaim): Boolean {
        return outboxRepository.releaseClaim(
            eventId = claim.eventId,
            claimToken = claim.claimToken,
            updatedAt = LocalDateTime.now(clock),
        ) == 1
    }

    private fun claim(
        outbox: OutboxEntity,
        now: LocalDateTime,
    ): OutboxClaim {
        val claimToken = UUID.randomUUID().toString()
        outbox.claim(claimToken, now.plus(properties.leaseDuration))
        return OutboxClaim(
            eventId = outbox.id,
            eventType = outbox.eventType,
            payload = outbox.payload,
            claimToken = claimToken,
        )
    }
}
