package io.plady.moimyeon.core.notification

// 한 사실의 알림은 전부 넣거나 하나도 넣지 않는다. 일부만 넣고 재시도하면 중복 발송된다.
fun interface NotificationMessagePublisher {
    fun publish(notifications: List<OutgoingNotification>)
}
