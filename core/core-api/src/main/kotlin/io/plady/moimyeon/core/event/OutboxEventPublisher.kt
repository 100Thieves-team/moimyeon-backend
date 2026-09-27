package io.plady.moimyeon.core.event

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.event.payload.EventPayload
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class OutboxEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    fun publish(type: EventType, payload: EventPayload) {
        require(type.payloadClass.isInstance(payload)) {
            "이벤트 종류와 payload 가 맞지 않습니다. type=$type, payload=${payload::class.simpleName}"
        }
        // 저장은 트랜잭션 이벤트 리스너가 하므로 쓰기 트랜잭션 밖에서 발행하면 조용히 사라진다.
        check(
            TransactionSynchronizationManager.isActualTransactionActive() &&
                !TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
        ) {
            "outbox 이벤트는 쓰기 트랜잭션 안에서 발행해야 합니다. type=$type"
        }
        applicationEventPublisher.publishEvent(OutboxEvent(EventIdGenerator.generate(), type, payload))
    }
}
