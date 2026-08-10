package io.plady.moimyeon.client.webpush

import io.plady.moimyeon.worker.notification.delivery.InvalidWebPushRegistrationRemover
import io.plady.moimyeon.worker.notification.delivery.Notification
import io.plady.moimyeon.worker.notification.delivery.NotificationRecipient
import io.plady.moimyeon.worker.notification.delivery.PermanentWebPushDeliveryException
import io.plady.moimyeon.worker.notification.delivery.RetryableWebPushDeliveryException
import io.plady.moimyeon.worker.notification.delivery.WebPushSender

internal class FcmWebPushSender(
    private val gateway: FcmGateway,
    private val invalidRegistrationRemover: InvalidWebPushRegistrationRemover,
    actionBaseUrl: String,
) : WebPushSender {
    private val actionBaseUrl = actionBaseUrl.removeSuffix("/")

    override fun send(
        notification: Notification,
        recipient: NotificationRecipient,
    ) {
        val results = mutableListOf<FcmSendResult>()
        try {
            recipient.webPushRegistrations.chunked(FCM_MULTICAST_LIMIT).forEach { registrations ->
                results += gateway.send(notification.toRequest(registrations))
            }
        } finally {
            val invalidRegistrations = results.asSequence()
                .filter { it.status == FcmSendStatus.UNREGISTERED }
                .map { it.registration }
                .toSet()
            if (invalidRegistrations.isNotEmpty()) {
                invalidRegistrationRemover.remove(invalidRegistrations)
            }
        }

        if (results.any { it.status == FcmSendStatus.RETRYABLE_FAILURE }) {
            throw RetryableWebPushDeliveryException("FCM 웹 푸시 전송을 재시도해야 합니다.")
        }
        if (results.any { it.status == FcmSendStatus.PERMANENT_FAILURE }) {
            throw PermanentWebPushDeliveryException("FCM이 웹 푸시 요청을 거절했습니다.")
        }
    }

    private fun Notification.toRequest(registrations: List<String>) = FcmMulticastRequest(
        registrations = registrations,
        title = content.title,
        body = content.body,
        actionUrl = content.actionPath?.let { "$actionBaseUrl/${it.removePrefix("/")}" },
        data = mapOf(
            "eventId" to eventId.toString(),
            "eventType" to eventType.name,
        ),
    )
}

private const val FCM_MULTICAST_LIMIT = 500
