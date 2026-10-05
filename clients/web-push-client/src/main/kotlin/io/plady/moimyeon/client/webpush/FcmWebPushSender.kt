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
            // 묶음 호출이 예외로 끝나도 그때까지 받은 기기별 결과를 남긴다.
            log.debug { "web-push.fcm.result ${notification.logFields()} ${results.summary()}" }
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
            log.warn { "web-push.fcm.rejected ${notification.logFields()} ${results.summary()}" }
        }
        return WebPushDelivery.UNDELIVERED
    }

    private fun Notification.logFields() = "eventId=$eventId eventType=$eventType recipientMemberId=$recipientMemberId"

    // 등록 토큰 자체는 자격 증명이라 남기지 않고 개수와 FCM 오류 코드만 남긴다.
    private fun List<FcmSendResult>.summary(): String {
        val counts = groupingBy { it.status }.eachCount()
        val errorCodes = mapNotNull { it.errorCode }
            .groupingBy { it }
            .eachCount()
            .entries
            .joinToString(",") { (code, count) -> "$code:$count" }
        return "registrations=$size" +
            " success=${counts[FcmSendStatus.SUCCESS] ?: 0}" +
            " unregistered=${counts[FcmSendStatus.UNREGISTERED] ?: 0}" +
            " retryableFailure=${counts[FcmSendStatus.RETRYABLE_FAILURE] ?: 0}" +
            " permanentFailure=${counts[FcmSendStatus.PERMANENT_FAILURE] ?: 0}" +
            " errorCodes=${errorCodes.ifEmpty { "-" }}"
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
