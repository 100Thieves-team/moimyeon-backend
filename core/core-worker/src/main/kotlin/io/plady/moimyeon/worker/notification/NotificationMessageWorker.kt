package io.plady.moimyeon.worker.notification

import io.plady.moimyeon.storage.redis.NotificationStreamConsumer
import io.plady.moimyeon.storage.redis.NotificationStreamMessage
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NotificationMessageWorker(
    private val messageConsumer: NotificationStreamConsumer,
    private val handlers: List<NotificationMessageHandler>,
) {
    @Scheduled(
        fixedDelayString = "\${notification.worker.consumer.fixed-delay:1s}",
        initialDelayString = "\${notification.worker.consumer.initial-delay:5s}",
    )
    fun consumeMessages() {
        if (handlers.isEmpty()) {
            return
        }
        messageConsumer.recoverPending(::handle)
        messageConsumer.consumeNew(::handle)
    }

    private fun handle(message: NotificationStreamMessage) {
        val handler = handlers.singleOrNull { it.eventType == message.eventType }
            ?: throw IllegalStateException("이벤트 처리기를 찾을 수 없습니다. eventType=${message.eventType}")
        handler.handle(message)
    }
}
