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
                val delivery = sendWebPush(notification, recipient)
                // 푸시 재시도 오류는 메일 없이 던진다. 메일을 먼저 보내면 재시도 때 중복된다.
                if (delivery == WebPushDelivery.UNDELIVERED && notification.policy.emailsWhenPushUndelivered) {
                    emailSender.send(notification, recipient)
                }
            }
            NotificationChannel.EMAIL -> emailSender.send(notification, recipient)
        }
    }

    private fun sendWebPush(
        notification: Notification,
        recipient: NotificationRecipient,
    ): WebPushDelivery {
        if (recipient.webPushRegistrations.isEmpty()) return WebPushDelivery.UNDELIVERED
        return webPushSender.send(notification, recipient)
    }
}
