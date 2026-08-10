package io.plady.moimyeon.core.enums

enum class EventType(
    val notificationChannels: Set<NotificationChannel>,
) {
    ROOM_APPLICATION_ACCEPTED(
        notificationChannels = setOf(NotificationChannel.WEB_PUSH, NotificationChannel.EMAIL),
    ),
}
