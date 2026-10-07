package io.plady.moimyeon.core.enums

// PUSH_ELSE_EMAIL 은 푸시가 한 기기에도 닿지 않으면 worker 가 이어서 메일을 보낸다.
enum class NotificationPolicy(
    val channels: Set<NotificationChannel>,
) {
    PUSH_ONLY(setOf(NotificationChannel.WEB_PUSH)),
    PUSH_ELSE_EMAIL(setOf(NotificationChannel.WEB_PUSH)),
    EMAIL_ONLY(setOf(NotificationChannel.EMAIL)),
    PUSH_AND_EMAIL(setOf(NotificationChannel.WEB_PUSH, NotificationChannel.EMAIL)),
    ;

    val emailsWhenPushUndelivered: Boolean
        get() = this == PUSH_ELSE_EMAIL
}
