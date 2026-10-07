package io.plady.moimyeon.storage.redis

interface NotificationStreamConsumer {
    fun recoverPending(handler: (NotificationStreamMessage) -> NotificationStreamHandlingResult): Int

    fun consumeNew(handler: (NotificationStreamMessage) -> NotificationStreamHandlingResult): Int
}

class NotificationStreamHandlingResult private constructor(
    val status: NotificationStreamHandlingStatus,
    val failureType: String? = null,
    val failureMessage: String? = null,
    val cause: Throwable? = null,
) {
    val isSuccess: Boolean
        get() = status == NotificationStreamHandlingStatus.SUCCESS

    val isRetryableFailure: Boolean
        get() = status == NotificationStreamHandlingStatus.RETRYABLE_FAILURE

    val isPermanentFailure: Boolean
        get() = status == NotificationStreamHandlingStatus.PERMANENT_FAILURE

    companion object {
        fun success() = NotificationStreamHandlingResult(NotificationStreamHandlingStatus.SUCCESS)

        fun retryableFailure(
            failureType: String,
            failureMessage: String?,
            cause: Throwable? = null,
        ) = NotificationStreamHandlingResult(
            status = NotificationStreamHandlingStatus.RETRYABLE_FAILURE,
            failureType = failureType,
            failureMessage = failureMessage,
            cause = cause,
        )

        fun permanentFailure(
            failureType: String,
            failureMessage: String?,
            cause: Throwable? = null,
        ) = NotificationStreamHandlingResult(
            status = NotificationStreamHandlingStatus.PERMANENT_FAILURE,
            failureType = failureType,
            failureMessage = failureMessage,
            cause = cause,
        )
    }
}

enum class NotificationStreamHandlingStatus {
    SUCCESS,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
}
