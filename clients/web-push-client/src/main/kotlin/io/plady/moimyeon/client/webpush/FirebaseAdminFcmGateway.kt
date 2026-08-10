package io.plady.moimyeon.client.webpush

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import com.google.firebase.messaging.SendResponse
import com.google.firebase.messaging.WebpushConfig
import com.google.firebase.messaging.WebpushFcmOptions
import io.plady.moimyeon.worker.notification.delivery.PermanentWebPushDeliveryException
import io.plady.moimyeon.worker.notification.delivery.RetryableWebPushDeliveryException

internal class FirebaseAdminFcmGateway(
    private val firebaseMessaging: FirebaseMessaging,
) : FcmGateway {
    override fun send(request: FcmMulticastRequest): List<FcmSendResult> {
        val messageBuilder = MulticastMessage.builder()
            .addAllFids(request.registrations)
            .setNotification(
                Notification.builder()
                    .setTitle(request.title)
                    .setBody(request.body)
                    .build(),
            )
            .putAllData(request.data)
        request.actionUrl?.let { actionUrl ->
            messageBuilder.setWebpushConfig(
                WebpushConfig.builder()
                    .setFcmOptions(WebpushFcmOptions.withLink(actionUrl))
                    .build(),
            )
        }

        val responses = try {
            firebaseMessaging.sendEachForMulticast(messageBuilder.build()).responses
        } catch (exception: FirebaseMessagingException) {
            throw exception.toDeliveryException()
        }

        if (responses.size != request.registrations.size) {
            throw RetryableWebPushDeliveryException(
                "FCM 응답 수와 요청 등록 식별자 수가 일치하지 않습니다.",
            )
        }
        return request.registrations.zip(responses) { registration, response ->
            response.toResult(registration)
        }
    }

    private fun SendResponse.toResult(registration: String): FcmSendResult {
        if (isSuccessful) return FcmSendResult.success(registration)

        return when (exception?.messagingErrorCode) {
            MessagingErrorCode.UNREGISTERED -> FcmSendResult.unregistered(registration)
            MessagingErrorCode.INTERNAL,
            MessagingErrorCode.QUOTA_EXCEEDED,
            MessagingErrorCode.UNAVAILABLE,
            null,
            -> FcmSendResult.retryableFailure(registration)

            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.SENDER_ID_MISMATCH,
            MessagingErrorCode.THIRD_PARTY_AUTH_ERROR,
            -> FcmSendResult.permanentFailure(registration)
        }
    }

    private fun FirebaseMessagingException.toDeliveryException() = when (messagingErrorCode) {
        MessagingErrorCode.INTERNAL,
        MessagingErrorCode.QUOTA_EXCEEDED,
        MessagingErrorCode.UNAVAILABLE,
        null,
        -> RetryableWebPushDeliveryException("FCM에 일시적으로 웹 푸시를 전송할 수 없습니다.", this)

        MessagingErrorCode.INVALID_ARGUMENT,
        MessagingErrorCode.SENDER_ID_MISMATCH,
        MessagingErrorCode.THIRD_PARTY_AUTH_ERROR,
        MessagingErrorCode.UNREGISTERED,
        -> PermanentWebPushDeliveryException("FCM이 웹 푸시 요청을 거절했습니다.", this)
    }
}
