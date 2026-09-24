package io.plady.moimyeon.worker.notification

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
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
    private val sender = mockk<NotificationSender>()
    private val handler = NotificationMessageHandler(
        jsonMapper = JsonMapper.builder().addModule(kotlinModule()).build(),
        notificationSender = sender,
    )

    @Test
    fun `참가 신청 수락 이벤트는 웹 푸시와 이메일로 알린다`() {
        EventType.entries.forEach { eventType ->
            assertThat(eventType.notificationChannels).containsExactlyInAnyOrder(
                NotificationChannel.WEB_PUSH,
                NotificationChannel.EMAIL,
            )
        }
    }

    @Test
    fun `참가 신청 수락 payload를 신청자 알림으로 전달한다`() {
        val notification = slot<Notification>()
        every { sender.send(capture(notification)) } just Runs

        handler.handle(message())

        assertThat(notification.captured).isEqualTo(
            Notification(
                eventId = EVENT_ID,
                eventType = EventType.ROOM_APPLICATION_ACCEPTED,
                channel = NotificationChannel.WEB_PUSH,
                recipientMemberId = APPLICANT_ID,
                content = NotificationContent(
                    title = "참가 신청이 수락되었어요",
                    body = "모임에 참여할 수 있게 되었어요.",
                    actionPath = "/rooms/$ROOM_ID",
                ),
            ),
        )
    }

    @Test
    fun `payload를 해석할 수 없으면 발송하지 않고 실패한다`() {
        assertThatThrownBy {
            handler.handle(message(payload = "{invalid-json"))
        }.isInstanceOf(InvalidNotificationMessageException::class.java)

        verify(exactly = 0) { sender.send(any()) }
    }

    @Test
    fun `룸 생명주기 이벤트를 수신자별 알림으로 변환한다`() {
        val expectedContents = mapOf(
            EventType.ROOM_CONFIRMED to NotificationContent(
                title = "모임 진행이 확정되었어요",
                body = "확정된 일정과 참여자를 확인해 주세요.",
                actionPath = "/rooms/$ROOM_ID",
            ),
            EventType.ROOM_COMPLETED to NotificationContent(
                title = "모임이 완료되었어요",
                body = "방장이 참석 여부를 기록하면 결과를 확인할 수 있어요.",
                actionPath = "/rooms/$ROOM_ID",
            ),
            EventType.ROOM_CANCELED to NotificationContent(
                title = "모임이 취소되었어요",
                body = "참여 중이던 모임의 취소 내용을 확인해 주세요.",
                actionPath = "/rooms/$ROOM_ID",
            ),
            EventType.ROOM_REVIEW_REQUESTED to NotificationContent(
                title = "함께한 참여자의 후기를 남겨 주세요",
                body = "완료된 모임의 리뷰를 작성할 수 있어요.",
                actionPath = "/rooms/$ROOM_ID",
            ),
        )

        expectedContents.forEach { (eventType, content) ->
            val notification = slot<Notification>()
            every { sender.send(capture(notification)) } just Runs

            handler.handle(lifecycleMessage(eventType))

            assertThat(notification.captured).isEqualTo(
                Notification(
                    eventId = EVENT_ID,
                    eventType = eventType,
                    channel = NotificationChannel.WEB_PUSH,
                    recipientMemberId = APPLICANT_ID,
                    content = content,
                ),
            )
        }
    }

    @Test
    fun `Stream과 payload의 이벤트 식별자가 다르면 발송하지 않는다`() {
        val otherEventId = UUID.fromString("0198b4f4-2f00-7000-8000-000000000099")

        assertThatThrownBy {
            handler.handle(message(payload = payload(eventId = otherEventId)))
        }.isInstanceOf(InvalidNotificationMessageException::class.java)

        verify(exactly = 0) { sender.send(any()) }
    }

    @Test
    fun `알림 발송이 실패하면 예외를 호출자에게 전파한다`() {
        every { sender.send(any()) } throws IllegalStateException("알림 발송 실패")

        assertThatThrownBy {
            handler.handle(message())
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("알림 발송 실패")
    }

    private fun message(payload: String = payload()) = NotificationStreamMessage(
        eventId = EVENT_ID,
        eventType = EventType.ROOM_APPLICATION_ACCEPTED,
        channel = NotificationChannel.WEB_PUSH,
        payload = payload,
    )

    private fun lifecycleMessage(eventType: EventType) = NotificationStreamMessage(
        eventId = EVENT_ID,
        eventType = eventType,
        channel = NotificationChannel.WEB_PUSH,
        payload = """
            {
              "eventId": "$EVENT_ID",
              "eventType": "$eventType",
              "roomId": "$ROOM_ID",
              "recipientMemberId": "$APPLICANT_ID"
            }
        """.trimIndent(),
    )

    private fun payload(eventId: UUID = EVENT_ID) =
        """
        {
          "eventId": "$eventId",
          "eventType": "ROOM_APPLICATION_ACCEPTED",
          "applicationId": 1,
          "roomId": "$ROOM_ID",
          "applicantMemberId": "$APPLICANT_ID"
        }
        """.trimIndent()

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("0198b4f4-2f00-7000-8000-000000000001")
        val ROOM_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val APPLICANT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }
}
