package io.plady.moimyeon.worker.notification.delivery

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.enums.NotificationChannel

private val log = KotlinLogging.logger {}

class ChannelNotificationSender(
    private val recipientFinder: NotificationRecipientFinder,
    private val webPushSender: WebPushSender,
    private val emailSender: EmailSender,
) : NotificationSender {
    override fun send(notification: Notification) {
        val recipient = recipientFinder.find(notification.recipientMemberId)
        when (notification.channel) {
            NotificationChannel.WEB_PUSH -> {
                val webPush = sendWebPush(notification, recipient)
                // 푸시 재시도 오류는 메일 없이 던진다. 메일을 먼저 보내면 재시도 때 중복된다.
                val email = if (!webPush.isDelivered && notification.policy.emailsWhenPushUndelivered) {
                    sendEmail(notification, recipient)
                } else {
                    EmailResult.NOT_REQUIRED
                }
                logCompleted(notification, recipient, webPush, email)
            }
            NotificationChannel.EMAIL -> {
                val email = sendEmail(notification, recipient)
                logCompleted(notification, recipient, WebPushResult.NOT_REQUIRED, email)
            }
        }
    }

    private fun sendEmail(
        notification: Notification,
        recipient: NotificationRecipient,
    ): EmailResult {
        if (!recipient.isActivityEmailEnabled) return EmailResult.SKIPPED_EMAIL_DISABLED
        emailSender.send(notification, recipient)
        return EmailResult.SENT
    }

    private fun sendWebPush(
        notification: Notification,
        recipient: NotificationRecipient,
    ): WebPushResult {
        if (!recipient.isWebPushAllowed) return WebPushResult.SKIPPED_PUSH_DISABLED
        if (recipient.webPushRegistrations.isEmpty()) return WebPushResult.SKIPPED_NO_REGISTRATION
        return when (webPushSender.send(notification, recipient)) {
            WebPushDelivery.DELIVERED -> WebPushResult.DELIVERED
            WebPushDelivery.UNDELIVERED -> WebPushResult.UNDELIVERED
        }
    }

    private fun logCompleted(
        notification: Notification,
        recipient: NotificationRecipient,
        webPush: WebPushResult,
        email: EmailResult,
    ) {
        log.debug {
            "notification.send.completed eventId=${notification.eventId} eventType=${notification.eventType}" +
                " channel=${notification.channel} policy=${notification.policy}" +
                " recipientMemberId=${notification.recipientMemberId}" +
                " registrations=${recipient.webPushRegistrations.size} webPush=$webPush email=$email"
        }
    }

    private enum class WebPushResult(
        val isDelivered: Boolean,
    ) {
        DELIVERED(true),
        UNDELIVERED(false),
        SKIPPED_PUSH_DISABLED(false),
        SKIPPED_NO_REGISTRATION(false),
        NOT_REQUIRED(false),
    }

    private enum class EmailResult {
        SENT,
        SKIPPED_EMAIL_DISABLED,
        NOT_REQUIRED,
    }
}
