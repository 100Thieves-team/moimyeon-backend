package io.plady.moimyeon.client.webpush

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.worker.notification.delivery.InvalidWebPushRegistrationRemover
import io.plady.moimyeon.worker.notification.delivery.Notification
import io.plady.moimyeon.worker.notification.delivery.NotificationRecipient
import io.plady.moimyeon.worker.notification.delivery.RetryableWebPushDeliveryException
import io.plady.moimyeon.worker.notification.delivery.WebPushDelivery
import io.plady.moimyeon.worker.notification.delivery.WebPushSender

private val log = KotlinLogging.logger {}

internal class FcmWebPushSender(
    private val gateway: FcmGateway,
    private val invalidRegistrationRemover: InvalidWebPushRegistrationRemover,
) : WebPushSender {
    // 한 기기라도 받았으면 재시도하지 않는다. 재시도하면 받은 기기에 중복 발송된다.
    override fun send(
        notification: Notification,
        recipient: NotificationRecipient,
    ): WebPushDelivery {
        val results = mutableListOf<FcmSendResult>()
        // 기기가 500대를 넘어 뒤 묶음이 실패하면 재시도 때 앞 묶음에 중복 발송된다. 드물어 받아들인다.
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

        if (results.any { it.status == FcmSendStatus.SUCCESS }) return WebPushDelivery.DELIVERED
        if (results.any { it.status == FcmSendStatus.RETRYABLE_FAILURE }) {
            throw RetryableWebPushDeliveryException("FCM 웹 푸시 전송을 재시도해야 합니다.")
        }
        if (results.any { it.status == FcmSendStatus.PERMANENT_FAILURE }) {
            log.warn { "web-push.fcm.rejected eventId=${notification.eventId} eventType=${notification.eventType}" }
        }
        return WebPushDelivery.UNDELIVERED
    }

    private fun Notification.toRequest(registrations: List<String>) = FcmMulticastRequest(
        registrations = registrations,
        title = content.title,
        body = content.body,
        actionUrl = content.actionUrl,
        data = mapOf(
            "eventId" to eventId.toString(),
            "eventType" to eventType,
        ),
    )
}

private const val FCM_MULTICAST_LIMIT = 500
