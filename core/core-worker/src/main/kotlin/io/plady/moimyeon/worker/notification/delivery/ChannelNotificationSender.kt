package io.plady.moimyeon.worker.notification.delivery

import io.plady.moimyeon.core.enums.NotificationChannel

class ChannelNotificationSender(
    private val recipientFinder: NotificationRecipientFinder,
    private val webPushSender: WebPushSender,
    private val emailSender: EmailSender,
) : NotificationSender {
    override fun send(notification: Notification) {
        val recipient = recipientFinder.find(notification.recipientMemberId)
        when (notification.channel) {
            NotificationChannel.WEB_PUSH -> {
                if (recipient.webPushRegistrations.isNotEmpty()) {
                    webPushSender.send(notification, recipient)
                }
            }
            NotificationChannel.EMAIL -> emailSender.send(notification, recipient)
        }
    }
}
