package io.plady.moimyeon.client.email

import io.plady.moimyeon.worker.notification.PermanentNotificationProcessingException
import io.plady.moimyeon.worker.notification.RetryableNotificationProcessingException

internal data class EmailMessage(
    val to: String,
    val subject: String,
    val body: String,
)

internal fun interface EmailDeliveryProvider {
    fun send(message: EmailMessage)
}

internal open class EmailDeliveryException(
    message: String,
    cause: Throwable? = null,
) : RetryableNotificationProcessingException(message, cause)

internal class EmailProviderUnavailableException(
    message: String,
    cause: Throwable? = null,
) : EmailDeliveryException(message, cause)

internal class PermanentEmailDeliveryException(
    message: String,
    cause: Throwable? = null,
) : PermanentNotificationProcessingException(message, cause)
