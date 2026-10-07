package io.plady.moimyeon.worker.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.storage.redis.NotificationStreamConsumer
import io.plady.moimyeon.storage.redis.NotificationStreamHandlingResult
import io.plady.moimyeon.storage.redis.NotificationStreamMessage
import org.springframework.scheduling.annotation.Scheduled

private val log = KotlinLogging.logger {}

class NotificationMessageWorker(
    private val messageConsumer: NotificationStreamConsumer,
    private val messageHandler: NotificationMessageHandler,
) {
    @Scheduled(
        fixedDelayString = "\${notification.worker.consumer.fixed-delay:1s}",
        initialDelayString = "\${notification.worker.consumer.initial-delay:5s}",
    )
    fun consumeMessages() {
        messageConsumer.recoverPending(::handle)
        messageConsumer.consumeNew(::handle)
    }

    private fun handle(message: NotificationStreamMessage): NotificationStreamHandlingResult = try {
        log.debug { "notification.message.handle eventId=${message.eventId} eventType=${message.eventType} channel=${message.channel}" }
        messageHandler.handle(message)
        NotificationStreamHandlingResult.success()
    } catch (exception: PermanentNotificationProcessingException) {
        NotificationStreamHandlingResult.permanentFailure(
            failureType = exception.javaClass.simpleName,
            failureMessage = exception.message,
            cause = exception,
        )
    } catch (exception: Exception) {
        NotificationStreamHandlingResult.retryableFailure(
            failureType = exception.javaClass.simpleName,
            failureMessage = exception.message,
            cause = exception,
        )
    }
}
