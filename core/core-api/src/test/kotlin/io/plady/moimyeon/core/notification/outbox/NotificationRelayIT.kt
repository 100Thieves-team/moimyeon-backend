package io.plady.moimyeon.core.notification.outbox

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.room.RoomApplicationAcceptedEvent
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.storage.db.core.OutboxRelayStatus
import io.plady.moimyeon.storage.db.core.OutboxRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Import(NotificationRelayTestConfiguration::class)
class NotificationRelayIT(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val outboxRepository: OutboxRepository,
    private val transactionTemplate: TransactionTemplate,
    private val messagePublisher: RecordingNotificationMessagePublisher,
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
    fun `트랜잭션이 커밋되면 저장된 Outbox를 즉시 전달하고 삭제한다`() {
        val event = applicationAcceptedEvent()

        transactionTemplate.executeWithoutResult {
            applicationEventPublisher.publishEvent(event)

            assertThat(messagePublisher.publishedMessages).isEmpty()
            assertThat(outboxRepository.count()).isZero()
        }

        assertThat(messagePublisher.awaitPublish()).isTrue()
        awaitCondition { outboxRepository.count() == 0L }

        assertThat(messagePublisher.publishedMessages).hasSize(1)
        val published = messagePublisher.publishedMessages.first()
        assertThat(published.eventId).isEqualTo(event.eventId)
        assertThat(published.eventType).isEqualTo(EventType.ROOM_APPLICATION_ACCEPTED)
        assertThat(published.payload).contains("\"applicationId\":1")
    }

    @Test
    fun `트랜잭션이 롤백되면 Outbox를 전달하지 않는다`() {
        assertThatThrownBy {
            transactionTemplate.executeWithoutResult {
                applicationEventPublisher.publishEvent(applicationAcceptedEvent())
                throw IllegalStateException("비즈니스 트랜잭션 실패")
            }
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(messagePublisher.awaitPublish(Duration.ofMillis(300))).isFalse()
        assertThat(messagePublisher.publishedMessages).isEmpty()
        assertThat(outboxRepository.count()).isZero()
    }

    @Test
    fun `전달이 실패하면 재시도할 수 있도록 Outbox를 남긴다`() {
        messagePublisher.failOnPublish = true
        val event = applicationAcceptedEvent()

        transactionTemplate.executeWithoutResult {
            applicationEventPublisher.publishEvent(event)
        }

        assertThat(messagePublisher.awaitPublish()).isTrue()
        awaitCondition {
            outboxRepository.findAll().singleOrNull()?.relayStatus == OutboxRelayStatus.PENDING
        }
        val released = outboxRepository.findAll().single()
        assertThat(released.id).isEqualTo(event.eventId)
        assertThat(released.relayStatus).isEqualTo(OutboxRelayStatus.PENDING)
        assertThat(released.claimToken).isNull()
        assertThat(released.leaseUntil).isNull()
    }

    private fun applicationAcceptedEvent() = RoomApplicationAcceptedEvent(
        eventId = UUID.fromString("0198b4f4-2f00-7000-8000-000000000001"),
        applicationId = 1L,
        roomId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        applicantMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
    )

    private fun awaitCondition(
        timeout: Duration = Duration.ofSeconds(2),
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertThat(condition()).isTrue()
    }
}

@TestConfiguration(proxyBeanMethods = false)
class NotificationRelayTestConfiguration {
    @Bean
    @Primary
    fun recordingNotificationMessagePublisher() = RecordingNotificationMessagePublisher()

    @Bean
    @Primary
    fun notificationTestClock(): Clock = Clock.fixed(
        Instant.parse("2026-08-10T12:00:00Z"),
        ZoneOffset.UTC,
    )
}

class RecordingNotificationMessagePublisher : NotificationMessagePublisher {
    val publishedMessages = CopyOnWriteArrayList<RelayMessage>()
    val publishedWithinTransactions = CopyOnWriteArrayList<Boolean>()

    @Volatile
    var failOnPublish: Boolean = false

    @Volatile
    private var publishLatch = CountDownLatch(1)

    private val blockNextPublish = AtomicBoolean(false)

    @Volatile
    private var blockedPublishStarted = CountDownLatch(1)

    @Volatile
    private var continueBlockedPublish = CountDownLatch(1)

    override fun publish(message: RelayMessage) {
        if (blockNextPublish.compareAndSet(true, false)) {
            blockedPublishStarted.countDown()
            continueBlockedPublish.await(5, TimeUnit.SECONDS)
        }
        publishedWithinTransactions += TransactionSynchronizationManager.isActualTransactionActive()
        publishedMessages += message
        publishLatch.countDown()
        if (failOnPublish) {
            throw IllegalStateException("Redis Stream 전달 실패")
        }
    }

    fun awaitPublish(timeout: Duration = Duration.ofSeconds(2)): Boolean = publishLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)

    fun blockNextPublish() {
        blockNextPublish.set(true)
    }

    fun awaitBlockedPublish(timeout: Duration = Duration.ofSeconds(2)): Boolean = blockedPublishStarted.await(
        timeout.toMillis(),
        TimeUnit.MILLISECONDS,
    )

    fun releaseBlockedPublish() {
        continueBlockedPublish.countDown()
    }

    fun reset() {
        continueBlockedPublish.countDown()
        publishedMessages.clear()
        publishedWithinTransactions.clear()
        failOnPublish = false
        publishLatch = CountDownLatch(1)
        blockNextPublish.set(false)
        blockedPublishStarted = CountDownLatch(1)
        continueBlockedPublish = CountDownLatch(1)
    }
}
