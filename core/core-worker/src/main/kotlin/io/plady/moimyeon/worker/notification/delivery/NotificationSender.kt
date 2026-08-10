package io.plady.moimyeon.worker.notification.delivery

import io.plady.moimyeon.worker.notification.PermanentNotificationProcessingException
import io.plady.moimyeon.worker.notification.RetryableNotificationProcessingException

fun interface NotificationSender {
    fun send(notification: Notification)
}

fun interface WebPushSender {
    fun send(
        notification: Notification,
        recipient: NotificationRecipient,
    )
}

fun interface InvalidWebPushRegistrationRemover {
    fun remove(registrations: Set<String>)
}

open class WebPushDeliveryException(
    message: String,
    cause: Throwable? = null,
) : RetryableNotificationProcessingException(message, cause)

class RetryableWebPushDeliveryException(
    message: String,
    cause: Throwable? = null,
) : WebPushDeliveryException(message, cause)

class PermanentWebPushDeliveryException(
    message: String,
    cause: Throwable? = null,
) : PermanentNotificationProcessingException(message, cause)

fun interface EmailSender {
    fun send(
        notification: Notification,
        recipient: NotificationRecipient,
    )
}
