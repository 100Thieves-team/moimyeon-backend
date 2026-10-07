package io.plady.moimyeon.core.event.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.event.OutboxEvent
import io.plady.moimyeon.core.event.OutboxEventConsumer
import io.plady.moimyeon.core.event.OutboxEventSerializer
import io.plady.moimyeon.core.event.UnknownOutboxEventTypeException
import io.plady.moimyeon.storage.db.core.OutboxEntity
import io.plady.moimyeon.storage.db.core.OutboxRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private val log = KotlinLogging.logger {}

@Component
class OutboxRelay(
    private val outboxRepository: OutboxRepository,
    private val outboxEventSerializer: OutboxEventSerializer,
    private val outboxClaimManager: OutboxClaimManager,
    private val consumers: List<OutboxEventConsumer>,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun record(event: OutboxEvent) {
        log.debug { "outbox.record eventId=${event.eventId} type=${event.type}" }
        outboxRepository.save(
            OutboxEntity(
                id = event.eventId,
                eventType = event.type.name,
                payload = outboxEventSerializer.serialize(event),
            ),
        )
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun relayAfterCommit(event: OutboxEvent) {
        log.debug { "outbox.relay-after-commit eventId=${event.eventId}" }
        val claim = outboxClaimManager.claim(event.eventId) ?: return
        relay(claim)
    }

    internal fun relay(claim: OutboxClaim) {
        log.debug { "outbox.relay eventId=${claim.eventId}" }
        val event = try {
            outboxEventSerializer.deserialize(claim.payload)
        } catch (exception: UnknownOutboxEventTypeException) {
            log.warn(exception) { "outbox.relay.unknown-type eventId=${claim.eventId}" }
            release(claim)
            return
        } catch (exception: Exception) {
            log.error(exception) { "outbox.relay.unreadable eventId=${claim.eventId}" }
            markUnreadable(claim)
            return
        }

        try {
            consumers.forEach { it.consume(event) }
        } catch (exception: Exception) {
            log.error(exception) { "outbox.relay.failed eventId=${claim.eventId}" }
            release(claim)
            return
        }
        complete(claim)
    }

    private fun complete(claim: OutboxClaim) {
        try {
            if (!outboxClaimManager.complete(claim)) {
                log.warn { "outbox.complete.skipped eventId=${claim.eventId} reason=claimed-elsewhere" }
            }
        } catch (exception: Exception) {
            log.error(exception) { "outbox.complete.failed eventId=${claim.eventId}" }
        }
    }

    private fun markUnreadable(claim: OutboxClaim) {
        try {
            if (!outboxClaimManager.markUnreadable(claim)) {
                log.warn { "outbox.mark-unreadable.skipped eventId=${claim.eventId} reason=claimed-elsewhere" }
            }
        } catch (exception: Exception) {
            log.error(exception) { "outbox.mark-unreadable.failed eventId=${claim.eventId}" }
        }
    }

    private fun release(claim: OutboxClaim) {
        try {
            if (!outboxClaimManager.release(claim)) {
                log.warn { "outbox.release.skipped eventId=${claim.eventId} reason=claimed-elsewhere" }
            }
        } catch (exception: Exception) {
            log.error(exception) { "outbox.release.failed eventId=${claim.eventId}" }
        }
    }
}
