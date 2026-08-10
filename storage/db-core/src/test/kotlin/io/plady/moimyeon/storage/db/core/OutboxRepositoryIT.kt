package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.storage.db.CoreDbContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OutboxRepositoryIT(
    private val outboxRepository: OutboxRepository,
    transactionManager: PlatformTransactionManager,
) : CoreDbContextTest() {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    @BeforeEach
    fun setUp() {
        outboxRepository.deleteAll()
    }

    @AfterEach
    fun cleanUp() {
        outboxRepository.deleteAll()
    }

    @Test
    fun `SKIP LOCKED는 다른 트랜잭션이 잠근 Outbox를 기다리거나 반환하지 않는다`() {
        saveOutbox(1)
        saveOutbox(2)
        val firstRowLocked = CountDownLatch(1)
        val releaseFirstRow = CountDownLatch(1)
        val firstEventId = AtomicReference<UUID>()
        val executor = Executors.newFixedThreadPool(2)

        try {
            val firstClaim = executor.submit {
                transactionTemplate.executeWithoutResult {
                    val claimed = outboxRepository.findClaimableBatchForUpdate(
                        createdBefore = TEST_TIME,
                        now = TEST_TIME,
                        batchSize = 1,
                    ).single()
                    firstEventId.set(claimed.id)
                    firstRowLocked.countDown()
                    releaseFirstRow.await(5, TimeUnit.SECONDS)
                }
            }
            assertThat(firstRowLocked.await(2, TimeUnit.SECONDS)).isTrue()

            val secondClaim = executor.submit {
                transactionTemplate.execute {
                    outboxRepository.findClaimableBatchForUpdate(
                        createdBefore = TEST_TIME,
                        now = TEST_TIME,
                        batchSize = 1,
                    ).firstOrNull()?.id
                }
            }
            val secondEventId = secondClaim.get(2, TimeUnit.SECONDS)

            assertThat(secondEventId).isNotEqualTo(firstEventId.get())
            releaseFirstRow.countDown()
            firstClaim.get(2, TimeUnit.SECONDS)
        } finally {
            releaseFirstRow.countDown()
            executor.shutdownNow()
        }
    }

    private fun saveOutbox(applicationId: Long) {
        outboxRepository.saveAndFlush(
            OutboxEntity(
                id = eventId(applicationId),
                eventType = EventType.ROOM_APPLICATION_ACCEPTED,
                payload = "{\"applicationId\":$applicationId}",
            ),
        )
    }

    private companion object {
        val TEST_TIME: LocalDateTime = LocalDateTime.of(2099, 1, 1, 0, 0)

        fun eventId(value: Long): UUID = UUID.fromString(
            "0198b4f4-2f00-7000-8000-${value.toString().padStart(12, '0')}",
        )
    }
}
