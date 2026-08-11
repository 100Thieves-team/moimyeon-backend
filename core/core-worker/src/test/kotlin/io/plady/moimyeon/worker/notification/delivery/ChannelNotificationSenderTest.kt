package io.plady.moimyeon.worker.notification.delivery

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ChannelNotificationSenderTest {
    @Test
    fun `웹 푸시 메시지는 웹 푸시만 발송한다`() {
        val fixture = fixture()

        fixture.sender.send(notification(NotificationChannel.WEB_PUSH))

        assertThat(fixture.webPushSender.sentRegistrations)
            .containsExactly(setOf("push-registration-1", "push-registration-2"))
        assertThat(fixture.emailSender.attemptCount).isZero()
    }

    @Test
    fun `이메일 메시지는 이메일만 발송한다`() {
        val fixture = fixture()

        fixture.sender.send(notification(NotificationChannel.EMAIL))

        assertThat(fixture.webPushSender.attemptCount).isZero()
        assertThat(fixture.emailSender.sentEmails).containsExactly("applicant@example.com")
    }

    @Test
    fun `외부 발송 실패를 소비 경계로 전파한다`() {
        val fixture = fixture()
        fixture.webPushSender.failOnSend = true

        assertThatThrownBy { fixture.sender.send(notification(NotificationChannel.WEB_PUSH)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("웹 푸시 실패")

        assertThat(fixture.webPushSender.attemptCount).isEqualTo(1)
        assertThat(fixture.emailSender.attemptCount).isZero()
    }

    @Test
    fun `웹 푸시 구독이 없으면 외부 호출 없이 완료한다`() {
        val fixture = fixture(webPushRegistrations = emptySet())

        fixture.sender.send(notification(NotificationChannel.WEB_PUSH))

        assertThat(fixture.webPushSender.attemptCount).isZero()
        assertThat(fixture.emailSender.attemptCount).isZero()
    }

    @Test
    fun `동일한 채널 메시지를 재처리하면 외부 채널을 다시 호출한다`() {
        val fixture = fixture()
        val notification = notification(NotificationChannel.EMAIL)

        fixture.sender.send(notification)
        fixture.sender.send(notification)

        assertThat(fixture.emailSender.attemptCount).isEqualTo(2)
    }

    private fun fixture(webPushRegistrations: Set<String> = setOf("push-registration-1", "push-registration-2")): DeliveryFixture {
        val recipientFinder = RecordingNotificationRecipientFinder(
            NotificationRecipient(
                email = "applicant@example.com",
                webPushRegistrations = webPushRegistrations,
            ),
        )
        val webPushSender = RecordingWebPushSender()
        val emailSender = RecordingEmailSender()
        return DeliveryFixture(
            sender = ChannelNotificationSender(
                recipientFinder = recipientFinder,
                webPushSender = webPushSender,
                emailSender = emailSender,
            ),
            webPushSender = webPushSender,
            emailSender = emailSender,
        )
    }

    private fun notification(channel: NotificationChannel) = Notification(
        eventId = EVENT_ID,
        eventType = EventType.ROOM_APPLICATION_ACCEPTED,
        channel = channel,
        recipientMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        content = NotificationContent(
            title = "참가 신청이 수락되었어요",
            body = "모임에 참여할 수 있게 되었어요.",
            actionPath = "/rooms/00000000-0000-0000-0000-000000000001",
        ),
    )

    private data class DeliveryFixture(
        val sender: ChannelNotificationSender,
        val webPushSender: RecordingWebPushSender,
        val emailSender: RecordingEmailSender,
    )

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("0198b4f4-2f00-7000-8000-000000000001")
    }
}

private class RecordingNotificationRecipientFinder(
    private val recipient: NotificationRecipient,
) : NotificationRecipientFinder {
    override fun find(memberId: UUID): NotificationRecipient = recipient
}

private class RecordingWebPushSender : WebPushSender {
    val sentRegistrations = mutableListOf<Set<String>>()
    var attemptCount: Int = 0
    var failOnSend: Boolean = false

    override fun send(notification: Notification, recipient: NotificationRecipient) {
        attemptCount++
        if (failOnSend) {
            throw IllegalStateException("웹 푸시 실패")
        }
        sentRegistrations += recipient.webPushRegistrations
    }
}

private class RecordingEmailSender : EmailSender {
    val sentEmails = mutableListOf<String>()
    var attemptCount: Int = 0

    override fun send(notification: Notification, recipient: NotificationRecipient) {
        attemptCount++
        sentEmails += recipient.email
    }
}
