package io.plady.moimyeon.client.email

import io.plady.moimyeon.worker.notification.delivery.EmailSender
import io.plady.moimyeon.worker.notification.delivery.Notification
import io.plady.moimyeon.worker.notification.delivery.NotificationRecipient

internal class FailoverEmailSender(
    private val primary: EmailDeliveryProvider,
    private val fallback: EmailDeliveryProvider,
) : EmailSender {
    override fun send(
        notification: Notification,
        recipient: NotificationRecipient,
    ) {
        val message = EmailMessage(
            to = recipient.email,
            subject = notification.content.title,
            body = listOfNotNull(
                notification.content.body,
                notification.content.actionPath,
            ).joinToString("\n\n"),
        )

        try {
            primary.send(message)
        } catch (primaryFailure: EmailProviderUnavailableException) {
            try {
                fallback.send(message)
            } catch (fallbackFailure: RuntimeException) {
                fallbackFailure.addSuppressed(primaryFailure)
                throw fallbackFailure
            }
        }
    }
}
