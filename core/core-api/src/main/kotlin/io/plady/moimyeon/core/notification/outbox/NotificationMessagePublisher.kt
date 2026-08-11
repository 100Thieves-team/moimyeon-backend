package io.plady.moimyeon.core.notification.outbox

fun interface NotificationMessagePublisher {
    fun publish(message: RelayMessage)
}
