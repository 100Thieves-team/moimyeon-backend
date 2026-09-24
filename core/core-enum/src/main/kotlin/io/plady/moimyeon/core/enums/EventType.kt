package io.plady.moimyeon.core.enums

enum class EventType(
    val notificationChannels: Set<NotificationChannel>,
) {
    ROOM_APPLICATION_ACCEPTED(
        notificationChannels = setOf(NotificationChannel.WEB_PUSH, NotificationChannel.EMAIL),
    ),
    ROOM_CONFIRMED(
        notificationChannels = setOf(NotificationChannel.WEB_PUSH, NotificationChannel.EMAIL),
    ),
    ROOM_COMPLETED(
        notificationChannels = setOf(NotificationChannel.WEB_PUSH, NotificationChannel.EMAIL),
    ),
    ROOM_CANCELED(
        notificationChannels = setOf(NotificationChannel.WEB_PUSH, NotificationChannel.EMAIL),
    ),
    ROOM_REVIEW_REQUESTED(
        notificationChannels = setOf(NotificationChannel.WEB_PUSH, NotificationChannel.EMAIL),
    ),
}
