package io.plady.moimyeon.client.email

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
import io.plady.moimyeon.worker.notification.delivery.Notification
import io.plady.moimyeon.worker.notification.delivery.NotificationContent
import io.plady.moimyeon.worker.notification.delivery.NotificationRecipient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import java.util.UUID

class FailoverEmailSenderTest {
    @Test
    fun `SES 전송에 성공하면 Gmail을 호출하지 않는다`() {
        val ses = RecordingEmailDeliveryProvider()
        val gmail = RecordingEmailDeliveryProvider()
        val sender = FailoverEmailSender(ses, gmail)

        sender.send(notification(), recipient())

        assertThat(ses.messages).containsExactly(expectedMessage())
        assertThat(gmail.messages).isEmpty()
    }

    @Test
    fun `SES의 일시적 실패에는 Gmail로 전송한다`() {
        val sesFailure = EmailProviderUnavailableException("SES unavailable")
        val ses = RecordingEmailDeliveryProvider(sesFailure)
        val gmail = RecordingEmailDeliveryProvider()
        val sender = FailoverEmailSender(ses, gmail)

        sender.send(notification(), recipient())

        assertThat(gmail.messages).containsExactly(expectedMessage())
    }

    @Test
    fun `SES의 영구 실패에는 Gmail로 전송하지 않는다`() {
        val sesFailure = PermanentEmailDeliveryException("invalid recipient")
        val gmail = RecordingEmailDeliveryProvider()
        val sender = FailoverEmailSender(RecordingEmailDeliveryProvider(sesFailure), gmail)

        assertThatThrownBy { sender.send(notification(), recipient()) }
            .isSameAs(sesFailure)
        assertThat(gmail.messages).isEmpty()
    }

    @Test
    fun `SES의 일시적 실패 후 Gmail도 실패하면 두 실패 원인을 보존한다`() {
        val sesFailure = EmailProviderUnavailableException("SES unavailable")
        val gmailFailure = EmailDeliveryException("Gmail unavailable")
        val sender = FailoverEmailSender(
            RecordingEmailDeliveryProvider(sesFailure),
            RecordingEmailDeliveryProvider(gmailFailure),
        )

        val failure = catchThrowable { sender.send(notification(), recipient()) }

        assertThat(failure).isSameAs(gmailFailure)
        assertThat(failure.suppressed).containsExactly(sesFailure)
    }

    private fun notification() = Notification(
        eventId = EVENT_ID,
        eventType = EventType.ROOM_APPLICATION_ACCEPTED,
        channel = NotificationChannel.EMAIL,
        recipientMemberId = MEMBER_ID,
        content = NotificationContent(
            title = "참가 신청이 수락되었어요",
            body = "모임에 참여할 수 있게 되었어요.",
            actionPath = "/rooms/$ROOM_ID",
        ),
    )

    private fun recipient() = NotificationRecipient(
        email = "member@example.com",
        webPushRegistrations = emptySet(),
    )

    private fun expectedMessage() = EmailMessage(
        to = "member@example.com",
        subject = "참가 신청이 수락되었어요",
        body = "모임에 참여할 수 있게 되었어요.\n\n/rooms/$ROOM_ID",
    )
}

private class RecordingEmailDeliveryProvider(
    private val failure: RuntimeException? = null,
) : EmailDeliveryProvider {
    val messages = mutableListOf<EmailMessage>()

    override fun send(message: EmailMessage) {
        failure?.let { throw it }
        messages += message
    }
}

private val EVENT_ID: UUID = UUID.fromString("01944e3f-4be2-7cc3-8ca4-dacc9c3f28ee")
private val MEMBER_ID: UUID = UUID.fromString("01944e3f-4be2-7cc3-8ca4-dacc9c3f28ef")
private val ROOM_ID: UUID = UUID.fromString("01944e3f-4be2-7cc3-8ca4-dacc9c3f28f0")
