package io.plady.moimyeon.core.notification.outbox

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.storage.db.core.OutboxEntity
import io.plady.moimyeon.storage.db.core.OutboxRelayStatus
import io.plady.moimyeon.storage.db.core.OutboxRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Import(NotificationRelayTestConfiguration::class)
@TestPropertySource(
    properties = [
        "notification.outbox.relay.batch-size=2",
        "notification.outbox.relay.stale-after=10s",
        "notification.outbox.relay.lease-duration=1m",
    ],
)
class PendingOutboxRelaySchedulerIT(
    private val pendingOutboxRelayScheduler: PendingOutboxRelayScheduler,
    private val outboxRepository: OutboxRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val messagePublisher: RecordingNotificationMessagePublisher,
    private val outboxClaimManager: OutboxClaimManager,
    private val clock: Clock,
) : ContextTest() {
    @BeforeEach
    fun setUp() {
        messagePublisher.reset()
        outboxRepository.deleteAll()
    }

    @AfterEach
    fun cleanUp() {
        outboxRepository.deleteAll()
    }

    @Test
    fun `일정 시간 이상 남은 Outbox만 다시 전달하고 성공하면 삭제한다`() {
        val pending = saveOutbox(1)
        val recent = saveOutbox(2)
        changeCreatedAt(pending.id, now().minusSeconds(11))

        pendingOutboxRelayScheduler.relayPendingOutboxes()

        assertThat(messagePublisher.publishedMessages.map { it.eventId })
            .containsExactly(pending.id)
        assertThat(outboxRepository.existsById(pending.id)).isFalse()
        assertThat(outboxRepository.existsById(recent.id)).isTrue()
    }

    @Test
    fun `한 번에 오래된 Outbox부터 설정된 배치 크기만 전달한다`() {
        val oldest = saveOutbox(1)
        val middle = saveOutbox(2)
        val newest = saveOutbox(3)
        changeCreatedAt(oldest.id, now().minusSeconds(30))
        changeCreatedAt(middle.id, now().minusSeconds(20))
        changeCreatedAt(newest.id, now().minusSeconds(15))

        pendingOutboxRelayScheduler.relayPendingOutboxes()

        assertThat(messagePublisher.publishedMessages.map { it.eventId })
            .containsExactly(oldest.id, middle.id)
        assertThat(outboxRepository.existsById(oldest.id)).isFalse()
        assertThat(outboxRepository.existsById(middle.id)).isFalse()
        assertThat(outboxRepository.existsById(newest.id)).isTrue()
    }

    @Test
    fun `재전달이 실패하면 다음 실행을 위해 Outbox를 남긴다`() {
        val pending = saveOutbox(1)
        changeCreatedAt(pending.id, now().minusSeconds(11))
        messagePublisher.failOnPublish = true

        pendingOutboxRelayScheduler.relayPendingOutboxes()

        assertThat(messagePublisher.publishedMessages.map { it.eventId })
            .containsExactly(pending.id)
        val released = outboxRepository.findById(pending.id).orElseThrow()
        assertThat(released.relayStatus).isEqualTo(OutboxRelayStatus.PENDING)
        assertThat(released.claimToken).isNull()
        assertThat(released.leaseUntil).isNull()
    }

    @Test
    fun `두 재전달 실행이 겹쳐도 같은 Outbox는 한 번만 발행한다`() {
        val pending = saveOutbox(1)
        changeCreatedAt(pending.id, now().minusSeconds(11))
        messagePublisher.blockNextPublish()
        val executor = Executors.newFixedThreadPool(2)

        try {
            val firstRelay = executor.submit(pendingOutboxRelayScheduler::relayPendingOutboxes)
            assertThat(messagePublisher.awaitBlockedPublish()).isTrue()

            val secondRelay = executor.submit(pendingOutboxRelayScheduler::relayPendingOutboxes)
            secondRelay.get(2, TimeUnit.SECONDS)

            messagePublisher.releaseBlockedPublish()
            firstRelay.get(2, TimeUnit.SECONDS)

            assertThat(messagePublisher.publishedMessages.map { it.eventId })
                .containsExactly(pending.id)
        } finally {
            messagePublisher.releaseBlockedPublish()
            executor.shutdownNow()
        }
    }

    @Test
    fun `lease가 남은 PROCESSING Outbox는 다른 실행이 가져가지 않는다`() {
        val processing = saveOutbox(1)
        changeCreatedAt(processing.id, now().minusSeconds(11))
        markProcessing(processing.id, now().plusSeconds(30))

        pendingOutboxRelayScheduler.relayPendingOutboxes()

        assertThat(messagePublisher.publishedMessages).isEmpty()
        assertThat(outboxRepository.existsById(processing.id)).isTrue()
    }

    @Test
    fun `PROCESSING Outbox는 lease가 만료되면 다시 전달한다`() {
        val abandoned = saveOutbox(1)
        changeCreatedAt(abandoned.id, now().minusSeconds(11))
        markProcessing(abandoned.id, now().minusSeconds(1))

        pendingOutboxRelayScheduler.relayPendingOutboxes()

        assertThat(messagePublisher.publishedMessages.map { it.eventId })
            .containsExactly(abandoned.id)
        assertThat(outboxRepository.existsById(abandoned.id)).isFalse()
    }

    @Test
    fun `재선점된 Outbox는 이전 소유자 토큰으로 완료하거나 해제할 수 없다`() {
        val pending = saveOutbox(1)
        val firstClaim = requireNotNull(outboxClaimManager.claim(pending.id))
        changeLeaseUntil(pending.id, now().minusSeconds(1))

        val secondClaim = requireNotNull(outboxClaimManager.claim(pending.id))

        assertThat(secondClaim.claimToken).isNotEqualTo(firstClaim.claimToken)
        assertThat(outboxClaimManager.complete(firstClaim)).isFalse()
        assertThat(outboxClaimManager.release(firstClaim)).isFalse()

        val claimed = outboxRepository.findById(pending.id).orElseThrow()
        assertThat(claimed.relayStatus).isEqualTo(OutboxRelayStatus.PROCESSING)
        assertThat(claimed.claimToken).isEqualTo(secondClaim.claimToken)
    }

    @Test
    fun `메시지 발행은 Outbox 선점 트랜잭션이 끝난 뒤 실행한다`() {
        val pending = saveOutbox(1)
        changeCreatedAt(pending.id, now().minusSeconds(11))

        pendingOutboxRelayScheduler.relayPendingOutboxes()

        assertThat(messagePublisher.publishedWithinTransactions).containsExactly(false)
    }

    private fun saveOutbox(applicationId: Long): OutboxEntity = outboxRepository.saveAndFlush(
        OutboxEntity(
            id = eventId(applicationId),
            eventType = EventType.ROOM_APPLICATION_ACCEPTED,
            payload = "{\"applicationId\":$applicationId}",
        ),
    )

    private fun changeCreatedAt(eventId: UUID, createdAt: LocalDateTime) {
        jdbcTemplate.update(
            "UPDATE outbox SET created_at = ? WHERE id = ?",
            Timestamp.valueOf(createdAt),
            eventId,
        )
    }

    private fun markProcessing(
        eventId: UUID,
        leaseUntil: LocalDateTime,
    ) {
        jdbcTemplate.update(
            """
                UPDATE outbox
                SET relay_status = 'PROCESSING', claim_token = 'existing-owner', lease_until = ?
                WHERE id = ?
            """.trimIndent(),
            Timestamp.valueOf(leaseUntil),
            eventId,
        )
    }

    private fun changeLeaseUntil(eventId: UUID, leaseUntil: LocalDateTime) {
        jdbcTemplate.update(
            "UPDATE outbox SET lease_until = ? WHERE id = ?",
            Timestamp.valueOf(leaseUntil),
            eventId,
        )
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    private fun eventId(value: Long): UUID = UUID.fromString(
        "0198b4f4-2f00-7000-8000-${value.toString().padStart(12, '0')}",
    )
}
