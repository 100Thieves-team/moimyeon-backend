package io.plady.moimyeon.client.webpush

internal data class FcmMulticastRequest(
    val registrations: List<String>,
    val title: String,
    val body: String,
    val actionUrl: String?,
    val data: Map<String, String>,
)

internal fun interface FcmGateway {
    fun send(request: FcmMulticastRequest): List<FcmSendResult>
}

internal data class FcmSendResult(
    val registration: String,
    val status: FcmSendStatus,
    // FCM이 거절한 이유. 성공했거나 FCM이 코드를 주지 않으면 null 이다.
    val errorCode: String? = null,
) {
    companion object {
        fun success(registration: String) = FcmSendResult(registration, FcmSendStatus.SUCCESS)

        fun unregistered(
            registration: String,
            errorCode: String? = null,
        ) = FcmSendResult(registration, FcmSendStatus.UNREGISTERED, errorCode)

        fun retryableFailure(
            registration: String,
            errorCode: String? = null,
        ) = FcmSendResult(registration, FcmSendStatus.RETRYABLE_FAILURE, errorCode)

        fun permanentFailure(
            registration: String,
            errorCode: String? = null,
        ) = FcmSendResult(registration, FcmSendStatus.PERMANENT_FAILURE, errorCode)
    }
}

internal enum class FcmSendStatus {
    SUCCESS,
    UNREGISTERED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
}
