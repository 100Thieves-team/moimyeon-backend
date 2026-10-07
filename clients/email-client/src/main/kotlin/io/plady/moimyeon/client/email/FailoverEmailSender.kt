package io.plady.moimyeon.client.email

import io.plady.moimyeon.worker.notification.delivery.EmailSender
import io.plady.moimyeon.worker.notification.delivery.Notification
import io.plady.moimyeon.worker.notification.delivery.NotificationRecipient

internal class FailoverEmailSender(
    private val primary: EmailDeliveryProvider,
    private val fallback: EmailDeliveryProvider,
    private val template: NotificationEmailTemplate,
) : EmailSender {
    override fun send(
        notification: Notification,
        recipient: NotificationRecipient,
    ) {
        val message = template.render(recipient.email, notification.content)

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
