package io.plady.moimyeon.client.webpush

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.plady.moimyeon.core.enums.NotificationChannel
import io.plady.moimyeon.core.enums.NotificationPolicy
import io.plady.moimyeon.worker.notification.delivery.InvalidWebPushRegistrationRemover
import io.plady.moimyeon.worker.notification.delivery.Notification
import io.plady.moimyeon.worker.notification.delivery.NotificationContent
import io.plady.moimyeon.worker.notification.delivery.NotificationRecipient
import io.plady.moimyeon.worker.notification.delivery.RetryableWebPushDeliveryException
import io.plady.moimyeon.worker.notification.delivery.WebPushDelivery
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.UUID

class FcmWebPushSenderTest {
    private val gateway = RecordingFcmGateway()
    private val invalidRegistrationRemover = RecordingInvalidWebPushRegistrationRemover()
    private val sender = FcmWebPushSender(
        gateway = gateway,
        invalidRegistrationRemover = invalidRegistrationRemover,
    )

    @Test
    fun `알림과 등록 식별자를 FCM 요청으로 변환한다`() {
        gateway.results = listOf(
            FcmSendResult.success("registration-1"),
            FcmSendResult.success("registration-2"),
        )

        sender.send(notification(), recipient("registration-1", "registration-2"))

        assertThat(gateway.requests).containsExactly(
            FcmMulticastRequest(
                registrations = listOf("registration-1", "registration-2"),
                title = "참가 신청 수락",
                body = "참가 신청이 수락되었습니다.",
                actionUrl = "https://front.test/interviews/room-1",
                data = mapOf(
                    "eventId" to EVENT_ID.toString(),
                    "eventType" to "ROOM_APPLICATION_ACCEPTED",
                ),
            ),
        )
    }

    @Test
    fun `FCM에서 만료되었다고 판정한 등록 식별자를 제거하고 완료한다`() {
        gateway.results = listOf(
            FcmSendResult.success("registration-1"),
            FcmSendResult.unregistered("expired-registration"),
        )

        sender.send(notification(), recipient("registration-1", "expired-registration"))

        assertThat(invalidRegistrationRemover.removed).containsExactly("expired-registration")
    }

    @Test
    fun `FCM이 기기별로 수락했는지와 거절 코드를 토큰 없이 로그로 남긴다`() {
        gateway.results = listOf(
            FcmSendResult.success("registration-1"),
            FcmSendResult.unregistered("expired-registration", "UNREGISTERED"),
            FcmSendResult.retryableFailure("retry-registration", "UNAVAILABLE"),
        )

        val messages = captureLogs {
            sender.send(notification(), recipient("registration-1", "expired-registration", "retry-registration"))
        }

        assertThat(messages).anySatisfy {
            assertThat(it)
                .startsWith("web-push.fcm.result eventId=$EVENT_ID")
                .contains(
                    "registrations=3",
                    "success=1",
                    "unregistered=1",
                    "retryableFailure=1",
                    "permanentFailure=0",
                    "UNREGISTERED:1",
                    "UNAVAILABLE:1",
                )
        }
        assertThat(messages).noneMatch { it.contains("registration-1") || it.contains("expired-registration") }
    }

    @Test
    fun `FCM 제한에 맞춰 등록 식별자를 최대 500개씩 나눈다`() {
        val registrations = (1..501).map { "registration-$it" }.toTypedArray()

        sender.send(notification(), recipient(*registrations))

        assertThat(gateway.requests.map { it.registrations.size }).containsExactly(500, 1)
    }

    private fun notification() = Notification(
        eventId = EVENT_ID,
        eventType = "ROOM_APPLICATION_ACCEPTED",
        channel = NotificationChannel.WEB_PUSH,
        policy = NotificationPolicy.PUSH_AND_EMAIL,
        recipientMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        content = NotificationContent(
            title = "참가 신청 수락",
            body = "참가 신청이 수락되었습니다.",
            actionUrl = "https://front.test/interviews/room-1",
        ),
    )

    private fun recipient(vararg registrations: String) = NotificationRecipient(
        email = "member@moimyeon.com",
        webPushRegistrations = registrations.toSet(),
        isWebPushAllowed = true,
        isActivityEmailEnabled = true,
    )

    @Test
    fun `한 기기라도 성공하면 다른 기기의 일시 오류가 있어도 전달됨으로 끝낸다`() {
        gateway.results = listOf(
            FcmSendResult.success("registration-1"),
            FcmSendResult.retryableFailure("retry-registration"),
        )

        val delivery = sender.send(notification(), recipient("registration-1", "retry-registration"))

        assertThat(delivery).isEqualTo(WebPushDelivery.DELIVERED)
    }

    @Test
    fun `성공한 기기가 없고 일시 오류가 있으면 만료 등록을 제거한 뒤 재시도 오류를 던진다`() {
        gateway.results = listOf(
            FcmSendResult.unregistered("expired-registration"),
            FcmSendResult.retryableFailure("retry-registration"),
        )

        assertThatThrownBy {
            sender.send(notification(), recipient("expired-registration", "retry-registration"))
        }.isInstanceOf(RetryableWebPushDeliveryException::class.java)
        assertThat(invalidRegistrationRemover.removed).containsExactly("expired-registration")
    }

    @Test
    fun `모든 기기가 만료 등록이면 등록을 제거하고 전달 안 됨으로 끝낸다`() {
        gateway.results = listOf(
            FcmSendResult.unregistered("expired-1"),
            FcmSendResult.unregistered("expired-2"),
        )

        val delivery = sender.send(notification(), recipient("expired-1", "expired-2"))

        assertThat(delivery).isEqualTo(WebPushDelivery.UNDELIVERED)
        assertThat(invalidRegistrationRemover.removed).containsExactlyInAnyOrder("expired-1", "expired-2")
    }

    @Test
    fun `성공한 기기 없이 영구 오류만 있으면 전달 안 됨으로 끝낸다`() {
        gateway.results = listOf(
            FcmSendResult.permanentFailure("invalid-registration"),
            FcmSendResult.unregistered("expired-registration"),
        )

        val delivery = sender.send(notification(), recipient("invalid-registration", "expired-registration"))

        assertThat(delivery).isEqualTo(WebPushDelivery.UNDELIVERED)
    }
}

private fun captureLogs(block: () -> Unit): List<String> {
    val logger = LoggerFactory.getLogger(FcmWebPushSender::class.java) as Logger
    val previousLevel = logger.level
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    logger.level = Level.DEBUG
    logger.addAppender(appender)
    try {
        block()
    } finally {
        logger.detachAppender(appender)
        logger.level = previousLevel
    }
    return appender.list.map { it.formattedMessage }
}

private class RecordingFcmGateway : FcmGateway {
    val requests = mutableListOf<FcmMulticastRequest>()
    var results = emptyList<FcmSendResult>()

    override fun send(request: FcmMulticastRequest): List<FcmSendResult> {
        requests += request
        return results
    }
}

private class RecordingInvalidWebPushRegistrationRemover : InvalidWebPushRegistrationRemover {
    val removed = mutableListOf<String>()

    override fun remove(registrations: Set<String>) {
        removed += registrations
    }
}

private val EVENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
