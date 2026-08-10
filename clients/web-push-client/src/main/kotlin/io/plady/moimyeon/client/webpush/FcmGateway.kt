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
) {
    companion object {
        fun success(registration: String) = FcmSendResult(registration, FcmSendStatus.SUCCESS)

        fun unregistered(registration: String) = FcmSendResult(registration, FcmSendStatus.UNREGISTERED)

        fun retryableFailure(registration: String) = FcmSendResult(registration, FcmSendStatus.RETRYABLE_FAILURE)

        fun permanentFailure(registration: String) = FcmSendResult(registration, FcmSendStatus.PERMANENT_FAILURE)
    }
}

internal enum class FcmSendStatus {
    SUCCESS,
    UNREGISTERED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
}
