package io.plady.moimyeon.core.notification.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.notification.NotificationEvent
import io.plady.moimyeon.storage.db.core.OutboxEntity
import io.plady.moimyeon.storage.db.core.OutboxRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.json.JsonMapper

private val log = KotlinLogging.logger {}

@Component
class NotificationRelay(
    private val outboxRepository: OutboxRepository,
    private val jsonMapper: JsonMapper,
    private val outboxClaimManager: OutboxClaimManager,
    private val messagePublisherProvider: ObjectProvider<NotificationMessagePublisher>,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun record(event: NotificationEvent) {
        log.debug { "outbox.record eventId=${event.eventId} eventType=${event.eventType}" }
        outboxRepository.save(
            OutboxEntity(
                id = event.eventId,
                eventType = event.eventType,
                payload = jsonMapper.writeValueAsString(event),
            ),
        )
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishEvent(event: NotificationEvent) {
        log.debug { "outbox.publish-event eventId=${event.eventId}" }
        val claim = outboxClaimManager.claim(event.eventId) ?: return
        publish(claim)
    }

    internal fun publish(claim: OutboxClaim) {
        log.debug { "outbox.publish eventId=${claim.eventId}" }
        val messagePublisher = messagePublisherProvider.ifAvailable
        if (messagePublisher == null) {
            release(claim)
            return
        }

        try {
            messagePublisher.publish(claim.relayMessage())
        } catch (exception: Exception) {
            log.error(exception) { "outbox.publish.failed eventId=${claim.eventId}" }
            release(claim)
            return
        }

        try {
            if (!outboxClaimManager.complete(claim)) {
                log.warn { "outbox.complete.skipped eventId=${claim.eventId} reason=claimed-elsewhere" }
            }
        } catch (exception: Exception) {
            log.error(exception) { "outbox.complete.failed eventId=${claim.eventId}" }
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
