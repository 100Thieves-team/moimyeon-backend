package io.plady.moimyeon.worker.notification.delivery

import java.util.UUID

data class NotificationRecipient(
    val email: String,
    val webPushRegistrations: Set<String>,
)

fun interface NotificationRecipientFinder {
    fun find(memberId: UUID): NotificationRecipient
}
