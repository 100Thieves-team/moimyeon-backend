package io.plady.moimyeon.worker.notification

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.storage.redis.NotificationStreamMessage

interface NotificationMessageHandler {
    val eventType: EventType

    fun handle(message: NotificationStreamMessage)
}
