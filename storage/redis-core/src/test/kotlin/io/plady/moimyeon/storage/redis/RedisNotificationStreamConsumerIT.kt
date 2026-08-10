package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
import io.plady.moimyeon.core.notification.outbox.RelayMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID

@Testcontainers
class RedisNotificationStreamConsumerIT {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var publisher: RedisNotificationMessagePublisher

    @BeforeEach
    fun setUp() {
        connectionFactory = connectionFactory(redis.host, redis.getMappedPort(REDIS_PORT))
        redisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        redisTemplate.delete(STREAM_KEY)
        redisTemplate.delete(DEAD_LETTER_STREAM_KEY)
        redisTemplate.delete(BROKEN_DEAD_LETTER_STREAM_KEY)
        publisher = RedisNotificationMessagePublisher(
            redisTemplate = redisTemplate,
            properties = RedisNotificationStreamProperties(STREAM_KEY),
        )
    }

    @AfterEach
    fun cleanUp() {
        redisTemplate.delete(STREAM_KEY)
        redisTemplate.delete(DEAD_LETTER_STREAM_KEY)
        redisTemplate.delete(BROKEN_DEAD_LETTER_STREAM_KEY)
        connectionFactory.destroy()
    }

    @Test
    fun `새 메시지 처리가 성공한 뒤에만 ACK한다`() {
        val eventId = eventId(21)
        publisher.publish(message(eventId))
        val handled = mutableListOf<NotificationStreamMessage>()

        consumer("worker-a").consumeNew {
            handled += it
            NotificationStreamHandlingResult.success()
        }

        assertThat(handled.map { it.eventId }).containsExactly(eventId, eventId)
        assertThat(handled.map { it.channel }).containsExactly(
            NotificationChannel.WEB_PUSH,
            NotificationChannel.EMAIL,
        )
        assertThat(pendingCount()).isZero()
    }

    @Test
    fun `메시지 처리에 실패하면 ACK하지 않고 Pending에 남긴다`() {
        publisher.publish(message(eventId(22)))

        consumer("worker-a").consumeNew {
            NotificationStreamHandlingResult.retryableFailure("ExternalFailure", "외부 전송 실패")
        }

        assertThat(pendingCount()).isEqualTo(2L)
    }

    @Test
    fun `다른 워커의 Pending 메시지를 재선점해 처리하고 ACK한다`() {
        val eventId = eventId(23)
        publisher.publish(message(eventId))
        consumer("worker-a").consumeNew {
            NotificationStreamHandlingResult.retryableFailure("WorkerStopped", "worker-a 종료")
        }
        val recovered = mutableListOf<NotificationStreamMessage>()

        consumer("worker-b").recoverPending {
            recovered += it
            NotificationStreamHandlingResult.success()
        }

        assertThat(recovered.map { it.eventId }).containsExactly(eventId, eventId)
        assertThat(recovered.map { it.channel }).containsExactly(
            NotificationChannel.WEB_PUSH,
            NotificationChannel.EMAIL,
        )
        assertThat(pendingCount()).isZero()
    }

    @Test
    fun `backoff 시간이 지나지 않은 Pending 메시지는 다시 처리하지 않는다`() {
        publisher.publish(message(eventId(24)))
        consumer("worker-a").consumeNew {
            NotificationStreamHandlingResult.retryableFailure("FcmUnavailable", "FCM 일시 장애")
        }
        var retried = false

        consumer(
            name = "worker-b",
            pendingMinIdle = Duration.ofHours(1),
        ).recoverPending {
            retried = true
            NotificationStreamHandlingResult.success()
        }

        assertThat(retried).isFalse()
        assertThat(pendingCount()).isEqualTo(2L)
    }

    @Test
    fun `영구 실패 메시지를 DLQ에 기록하고 원본을 ACK한다`() {
        publisher.publish(message(eventId(25)))

        consumer("worker-a").consumeNew {
            NotificationStreamHandlingResult.permanentFailure("InvalidPayload", "payload 불일치")
        }

        assertThat(pendingCount()).isZero()
        assertThat(deadLetters()).hasSize(2)
        deadLetters().forEach { record ->
            assertThat(record.value)
                .containsEntry("failureType", "InvalidPayload")
                .containsEntry("failureMessage", "payload 불일치")
                .containsEntry("attemptCount", "1")
                .containsKey("sourceRecordId")
        }
    }

    @Test
    fun `재시도 상한에 도달한 메시지를 DLQ에 기록하고 원본을 ACK한다`() {
        publisher.publish(message(eventId(26)))
        val consumer = consumer(
            name = "worker-a",
            maxAttempts = 2,
        )
        consumer.consumeNew {
            NotificationStreamHandlingResult.retryableFailure("FcmUnavailable", "FCM 일시 장애")
        }

        consumer.recoverPending {
            NotificationStreamHandlingResult.retryableFailure("FcmUnavailable", "FCM 일시 장애")
        }

        assertThat(pendingCount()).isZero()
        assertThat(deadLetters()).hasSize(2)
        deadLetters().forEach { record ->
            assertThat(record.value)
                .containsEntry("failureType", "RetryAttemptsExhausted")
                .containsEntry("attemptCount", "2")
        }
    }

    @Test
    fun `DLQ 기록에 실패하면 원본 메시지를 ACK하지 않는다`() {
        publisher.publish(message(eventId(27)))
        redisTemplate.opsForValue().set(BROKEN_DEAD_LETTER_STREAM_KEY, "not-a-stream")
        val consumer = consumer(
            name = "worker-a",
            deadLetterStreamKey = BROKEN_DEAD_LETTER_STREAM_KEY,
        )

        consumer.consumeNew {
            NotificationStreamHandlingResult.permanentFailure("InvalidPayload", "payload 불일치")
        }

        assertThat(pendingCount()).isEqualTo(2L)
    }

    private fun consumer(
        name: String,
        pendingMinIdle: Duration = Duration.ZERO,
        maxAttempts: Long = 5,
        deadLetterStreamKey: String = DEAD_LETTER_STREAM_KEY,
    ): NotificationStreamConsumer = RedisNotificationStreamConsumer(
        redisTemplate = redisTemplate,
        properties = RedisNotificationStreamConsumerProperties(
            groupName = GROUP_NAME,
            consumerName = name,
            batchSize = 10,
            pendingMinIdle = pendingMinIdle,
            retryMaxDelay = maxOf(pendingMinIdle, Duration.ofMinutes(10)),
            maxAttempts = maxAttempts,
        ),
        streamProperties = RedisNotificationStreamProperties(
            streamKey = STREAM_KEY,
            deadLetterStreamKey = deadLetterStreamKey,
        ),
    )

    private fun pendingCount(): Long = redisTemplate.opsForStream<String, String>()
        .pending(STREAM_KEY, GROUP_NAME)
        .totalPendingMessages

    private fun deadLetters() = redisTemplate.opsForStream<String, String>()
        .range(DEAD_LETTER_STREAM_KEY, org.springframework.data.domain.Range.unbounded<String>())

    private fun message(eventId: UUID) = RelayMessage(
        eventId = eventId,
        eventType = EventType.ROOM_APPLICATION_ACCEPTED,
        payload = "{\"eventId\":\"$eventId\"}",
    )

    private fun eventId(value: Long): UUID = UUID.fromString(
        "0198b4f4-2f00-7000-8000-${value.toString().padStart(12, '0')}",
    )

    private fun connectionFactory(host: String, port: Int): LettuceConnectionFactory {
        val clientConfiguration = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(1))
            .shutdownTimeout(Duration.ZERO)
            .build()
        return LettuceConnectionFactory(
            RedisStandaloneConfiguration(host, port),
            clientConfiguration,
        ).apply { afterPropertiesSet() }
    }

    private companion object {
        const val REDIS_PORT = 6379
        const val STREAM_KEY = "notification-events-consumer-test"
        const val DEAD_LETTER_STREAM_KEY = "notification-events-dead-letter-consumer-test"
        const val BROKEN_DEAD_LETTER_STREAM_KEY = "notification-events-broken-dead-letter-consumer-test"
        const val GROUP_NAME = "notification-workers-test"

        @Container
        @JvmStatic
        val redis = ConsumerRedisContainer(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(REDIS_PORT)
    }
}

private class ConsumerRedisContainer(imageName: DockerImageName) : GenericContainer<ConsumerRedisContainer>(imageName)
