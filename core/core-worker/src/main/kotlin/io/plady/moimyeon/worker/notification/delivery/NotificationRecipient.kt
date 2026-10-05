package io.plady.moimyeon.worker.notification.delivery

import java.util.UUID

data class NotificationRecipient(
    val email: String,
    val webPushRegistrations: Set<String>,
    val isWebPushAllowed: Boolean,
    val isActivityEmailEnabled: Boolean,
)

fun interface NotificationRecipientFinder {
    fun find(memberId: UUID): NotificationRecipient
}
