package io.plady.moimyeon.core.notification.outbox

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.EventType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider

class NotificationRelayTest {
    private val outboxClaimManager = mockk<OutboxClaimManager>()
    private val messagePublisher = mockk<NotificationMessagePublisher>(relaxed = true)
    private val messagePublisherProvider = mockk<ObjectProvider<NotificationMessagePublisher>>()
    private val notificationRelay = NotificationRelay(
        outboxRepository = mockk(relaxed = true),
        jsonMapper = mockk(relaxed = true),
        outboxClaimManager = outboxClaimManager,
        messagePublisherProvider = messagePublisherProvider,
    )

    @Test
    fun `메시지 발행 후 완료 기록이 실패하면 선점을 해제하지 않는다`() {
        val claim = OutboxClaim(
            eventId = java.util.UUID.fromString("0198b4f4-2f00-7000-8000-000000000001"),
            eventType = EventType.ROOM_APPLICATION_ACCEPTED,
            payload = "{\"applicationId\":1}",
            claimToken = "claim-token",
        )
        every { messagePublisherProvider.ifAvailable } returns messagePublisher
        every { outboxClaimManager.complete(claim) } throws IllegalStateException("DB 완료 기록 실패")

        notificationRelay.publish(claim)

        verify(exactly = 1) { messagePublisher.publish(claim.relayMessage()) }
        verify(exactly = 1) { outboxClaimManager.complete(claim) }
        verify(exactly = 0) { outboxClaimManager.release(any()) }
    }
}
