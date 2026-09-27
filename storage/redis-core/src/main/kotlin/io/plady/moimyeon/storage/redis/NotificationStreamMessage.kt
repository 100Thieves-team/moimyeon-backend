package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.core.enums.NotificationChannel
import java.util.UUID

// eventType 은 enum 으로 바꾸지 않는다. worker 가 모르는 이벤트도 버리지 않아야 API 를 먼저 배포할 수 있다.
data class NotificationStreamMessage(
    val eventId: UUID,
    val eventType: String,
    val channel: NotificationChannel,
    val payload: String,
)
