package io.plady.moimyeon.core.notification.outbox

import io.plady.moimyeon.core.notification.NotificationEvent
import io.plady.moimyeon.storage.db.core.OutboxEntity
import io.plady.moimyeon.storage.db.core.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.json.JsonMapper

@Component
class NotificationRelay(
    private val outboxRepository: OutboxRepository,
    private val jsonMapper: JsonMapper,
    private val outboxClaimManager: OutboxClaimManager,
    private val messagePublisherProvider: ObjectProvider<NotificationMessagePublisher>,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun record(event: NotificationEvent) {
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
        val claim = outboxClaimManager.claim(event.eventId) ?: return
        publish(claim)
    }

    internal fun publish(claim: OutboxClaim) {
        val messagePublisher = messagePublisherProvider.ifAvailable
        if (messagePublisher == null) {
            release(claim)
            return
        }

        try {
            messagePublisher.publish(claim.relayMessage())
        } catch (exception: Exception) {
            log.error("Outbox 전달에 실패했습니다. eventId={}", claim.eventId, exception)
            release(claim)
            return
        }

        try {
            if (!outboxClaimManager.complete(claim)) {
                log.warn("다른 실행이 선점한 Outbox는 완료 처리하지 않습니다. eventId={}", claim.eventId)
            }
        } catch (exception: Exception) {
            log.error("전달한 Outbox의 완료 처리에 실패했습니다. eventId={}", claim.eventId, exception)
        }
    }

    private fun release(claim: OutboxClaim) {
        try {
            if (!outboxClaimManager.release(claim)) {
                log.warn("다른 실행이 선점한 Outbox는 대기 상태로 되돌리지 않습니다. eventId={}", claim.eventId)
            }
        } catch (exception: Exception) {
            log.error("Outbox 대기 상태 복원에 실패했습니다. eventId={}", claim.eventId, exception)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(NotificationRelay::class.java)
    }
}
