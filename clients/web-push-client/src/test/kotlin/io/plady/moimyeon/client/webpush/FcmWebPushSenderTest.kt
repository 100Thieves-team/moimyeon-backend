package io.plady.moimyeon.client.webpush

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
import io.plady.moimyeon.worker.notification.delivery.InvalidWebPushRegistrationRemover
import io.plady.moimyeon.worker.notification.delivery.Notification
import io.plady.moimyeon.worker.notification.delivery.NotificationContent
import io.plady.moimyeon.worker.notification.delivery.NotificationRecipient
import io.plady.moimyeon.worker.notification.delivery.PermanentWebPushDeliveryException
import io.plady.moimyeon.worker.notification.delivery.RetryableWebPushDeliveryException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class FcmWebPushSenderTest {
    private val gateway = RecordingFcmGateway()
    private val invalidRegistrationRemover = RecordingInvalidWebPushRegistrationRemover()
    private val sender = FcmWebPushSender(
        gateway = gateway,
        invalidRegistrationRemover = invalidRegistrationRemover,
        actionBaseUrl = "https://moimyeon.com",
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
                actionUrl = "https://moimyeon.com/rooms/room-1",
                data = mapOf(
                    "eventId" to EVENT_ID.toString(),
                    "eventType" to EventType.ROOM_APPLICATION_ACCEPTED.name,
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
    fun `일시 오류가 하나라도 있으면 만료 등록을 제거한 뒤 재시도 오류를 던진다`() {
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
    fun `영구 오류만 있으면 영구 전송 오류를 던진다`() {
        gateway.results = listOf(FcmSendResult.permanentFailure("invalid-registration"))

        assertThatThrownBy {
            sender.send(notification(), recipient("invalid-registration"))
        }.isInstanceOf(PermanentWebPushDeliveryException::class.java)
    }

    @Test
    fun `FCM 제한에 맞춰 등록 식별자를 최대 500개씩 나눈다`() {
        val registrations = (1..501).map { "registration-$it" }.toTypedArray()

        sender.send(notification(), recipient(*registrations))

        assertThat(gateway.requests.map { it.registrations.size }).containsExactly(500, 1)
    }

    private fun notification() = Notification(
        eventId = EVENT_ID,
        eventType = EventType.ROOM_APPLICATION_ACCEPTED,
        channel = NotificationChannel.WEB_PUSH,
        recipientMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        content = NotificationContent(
            title = "참가 신청 수락",
            body = "참가 신청이 수락되었습니다.",
            actionPath = "/rooms/room-1",
        ),
    )

    private fun recipient(vararg registrations: String) = NotificationRecipient(
        email = "member@moimyeon.com",
        webPushRegistrations = registrations.toSet(),
    )
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
