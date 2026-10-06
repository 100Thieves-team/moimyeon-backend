package io.plady.moimyeon.worker.notification

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.enums.NotificationChannel
import io.plady.moimyeon.core.enums.NotificationPolicy
import io.plady.moimyeon.storage.redis.NotificationStreamMessage
import io.plady.moimyeon.worker.notification.delivery.Notification
import io.plady.moimyeon.worker.notification.delivery.NotificationContent
import io.plady.moimyeon.worker.notification.delivery.NotificationSender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.util.UUID

class NotificationMessageHandlerTest {
    private val notificationSender = mockk<NotificationSender>()
    private val notificationMessageHandler = NotificationMessageHandler(
        jsonMapper = JsonMapper.builder().addModule(kotlinModule()).build(),
        notificationSender = notificationSender,
        actionBaseUrl = FRONT_BASE_URL,
    )

    @Test
    fun `공통 형식 payload를 받는 사람과 문구 그대로 발송 요청으로 바꾼다`() {
        val notification = slot<Notification>()
        every { notificationSender.send(capture(notification)) } just Runs

        notificationMessageHandler.handle(message())

        assertThat(notification.captured).isEqualTo(
            Notification(
                eventId = EVENT_ID,
                eventType = "ROOM_APPLICATION_ACCEPTED",
                channel = NotificationChannel.WEB_PUSH,
                policy = NotificationPolicy.PUSH_AND_EMAIL,
                recipientMemberId = RECIPIENT_ID,
                content = NotificationContent(
                    title = "참가 신청이 수락되었어요",
                    body = "모임에 참여할 수 있게 되었어요.",
                    actionUrl = "$FRONT_BASE_URL/interviews/$ROOM_ID",
                ),
            ),
        )
    }

    @Test
    fun `프론트 주소와 상대 경로 사이의 슬래시는 하나로 합친다`() {
        val notification = slot<Notification>()
        every { notificationSender.send(capture(notification)) } just Runs
        val notificationMessageHandler = NotificationMessageHandler(
            jsonMapper = JsonMapper.builder().addModule(kotlinModule()).build(),
            notificationSender = notificationSender,
            actionBaseUrl = "$FRONT_BASE_URL/",
        )

        notificationMessageHandler.handle(message(payload = payload(actionPath = "/interviews/$ROOM_ID")))

        assertThat(notification.captured.content.actionUrl).isEqualTo("$FRONT_BASE_URL/interviews/$ROOM_ID")
    }

    @Test
    fun `worker가 모르는 이벤트 종류도 공통 형식이면 발송한다`() {
        val notification = slot<Notification>()
        every { notificationSender.send(capture(notification)) } just Runs

        notificationMessageHandler.handle(message(eventType = "SOME_FUTURE_EVENT"))

        assertThat(notification.captured.eventType).isEqualTo("SOME_FUTURE_EVENT")
    }

    @Test
    fun `이동 경로가 없으면 링크 없이 발송한다`() {
        val notification = slot<Notification>()
        every { notificationSender.send(capture(notification)) } just Runs

        notificationMessageHandler.handle(message(payload = payload(actionPath = null)))

        assertThat(notification.captured.content.actionUrl).isNull()
    }

    @Test
    fun `정책에 없는 채널의 메시지는 발송하지 않고 영구 실패한다`() {
        assertThatThrownBy {
            notificationMessageHandler.handle(message(payload = payload(policy = NotificationPolicy.EMAIL_ONLY)))
        }.isInstanceOf(InvalidNotificationMessageException::class.java)

        verify(exactly = 0) { notificationSender.send(any()) }
    }

    @Test
    fun `공통 형식의 필수 값이 없으면 발송하지 않고 영구 실패한다`() {
        val withoutRecipient = """{"eventId":"$EVENT_ID","policy":"PUSH_ONLY","title":"제목","body":"본문"}"""

        assertThatThrownBy {
            notificationMessageHandler.handle(message(payload = withoutRecipient))
        }.isInstanceOf(InvalidNotificationMessageException::class.java)

        verify(exactly = 0) { notificationSender.send(any()) }
    }

    @Test
    fun `제목이 비어 있으면 발송하지 않고 영구 실패한다`() {
        assertThatThrownBy {
            notificationMessageHandler.handle(message(payload = payload(title = " ")))
        }.isInstanceOf(InvalidNotificationMessageException::class.java)
    }

    @Test
    fun `payload를 해석할 수 없으면 발송하지 않고 실패한다`() {
        assertThatThrownBy {
            notificationMessageHandler.handle(message(payload = "{invalid-json"))
        }.isInstanceOf(InvalidNotificationMessageException::class.java)

        verify(exactly = 0) { notificationSender.send(any()) }
    }

    @Test
    fun `Stream과 payload의 이벤트 식별자가 다르면 발송하지 않는다`() {
        val otherEventId = UUID.fromString("0198b4f4-2f00-7000-8000-000000000099")

        assertThatThrownBy {
            notificationMessageHandler.handle(message(payload = payload(eventId = otherEventId)))
        }.isInstanceOf(InvalidNotificationMessageException::class.java)

        verify(exactly = 0) { notificationSender.send(any()) }
    }

    @Test
    fun `알림 발송이 실패하면 예외를 호출자에게 전파한다`() {
        every { notificationSender.send(any()) } throws IllegalStateException("알림 발송 실패")

        assertThatThrownBy {
            notificationMessageHandler.handle(message())
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("알림 발송 실패")
    }

    private fun message(
        eventType: String = "ROOM_APPLICATION_ACCEPTED",
        payload: String = payload(),
    ) = NotificationStreamMessage(
        eventId = EVENT_ID,
        eventType = eventType,
        channel = NotificationChannel.WEB_PUSH,
        payload = payload,
    )

    private fun payload(
        eventId: UUID = EVENT_ID,
        policy: NotificationPolicy = NotificationPolicy.PUSH_AND_EMAIL,
        title: String = "참가 신청이 수락되었어요",
        actionPath: String? = "/interviews/$ROOM_ID",
    ) = """
        {
          "eventId": "$eventId",
          "eventType": "ROOM_APPLICATION_ACCEPTED",
          "policy": "$policy",
          "recipientMemberId": "$RECIPIENT_ID",
          "title": "$title",
          "body": "모임에 참여할 수 있게 되었어요.",
          "actionPath": ${actionPath?.let { "\"$it\"" } ?: "null"}
        }
    """.trimIndent()

    private companion object {
        const val FRONT_BASE_URL = "https://front.test"
        val EVENT_ID: UUID = UUID.fromString("0198b4f4-2f00-7000-8000-000000000001")
        val ROOM_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val RECIPIENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }
}
