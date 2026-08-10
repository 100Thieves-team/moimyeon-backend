package io.plady.moimyeon.core.notification.outbox

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class PendingOutboxRelaySchedulerTest {
    private val outboxClaimManager = mockk<OutboxClaimManager>()
    private val notificationRelay = mockk<NotificationRelay>()
    private val relayCoordinator = mockk<OutboxRelayCoordinator>()

    private val scheduler = PendingOutboxRelayScheduler(
        outboxClaimManager = outboxClaimManager,
        notificationRelay = notificationRelay,
        relayCoordinator = relayCoordinator,
    )

    @Test
    fun `다른 인스턴스가 미처리 Outbox를 재전달 중이면 DB를 조회하지 않는다`() {
        every { relayCoordinator.relayPendingIfAvailable(any()) } returns false

        scheduler.relayPendingOutboxes()

        verify(exactly = 0) { outboxClaimManager.claimPendingBatch() }
    }

    @Test
    fun `재전달 실행 권한을 얻으면 미처리 Outbox를 조회한다`() {
        every { relayCoordinator.relayPendingIfAvailable(any()) } answers {
            firstArg<() -> Unit>().invoke()
            true
        }
        every { outboxClaimManager.claimPendingBatch() } returns emptyList()

        scheduler.relayPendingOutboxes()

        verify(exactly = 1) { outboxClaimManager.claimPendingBatch() }
    }
}
