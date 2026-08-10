package io.plady.moimyeon.storage.redis

interface NotificationStreamConsumer {
    fun recoverPending(handler: (NotificationStreamMessage) -> Unit): Int

    fun consumeNew(handler: (NotificationStreamMessage) -> Unit): Int
}
