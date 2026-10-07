package io.plady.moimyeon.worker.notification

open class RetryableNotificationProcessingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

open class PermanentNotificationProcessingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class InvalidNotificationMessageException(
    message: String,
    cause: Throwable? = null,
) : PermanentNotificationProcessingException(message, cause)
