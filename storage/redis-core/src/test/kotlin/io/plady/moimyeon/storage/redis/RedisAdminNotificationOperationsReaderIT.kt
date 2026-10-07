package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.admin.notification.AdminNotificationDashboard
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@Testcontainers
class RedisAdminNotificationOperationsReaderIT {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(
            RedisStandaloneConfiguration(redis.host, redis.getMappedPort(REDIS_PORT)),
        ).apply { afterPropertiesSet() }
        redisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        redisTemplate.delete(STREAM_KEY)
        redisTemplate.delete(DEAD_LETTER_STREAM_KEY)
    }

    @AfterEach
    fun cleanUp() {
        redisTemplate.delete(STREAM_KEY)
        redisTemplate.delete(DEAD_LETTER_STREAM_KEY)
        connectionFactory.destroy()
    }

    @Test
    fun `Stream과 Consumer Group이 없으면 빈 운영 현황을 반환한다`() {
        assertThat(reader().loadDashboard(50)).isEqualTo(
            AdminNotificationDashboard(
                pendingCount = 0,
                deadLetterCount = 0,
                recentDeadLetters = emptyList(),
            ),
        )
    }

    @Test
    fun `Pending 수와 최신 DLQ 메시지를 제한된 개수만 조회한다`() {
        addPendingMessage()
        addDeadLetter(eventId = "event-1", failedAt = "2026-08-11T00:00:00Z")
        addDeadLetter(eventId = "event-2", failedAt = "2026-08-11T00:01:00Z")

        val dashboard = reader().loadDashboard(1)

        assertThat(dashboard.pendingCount).isEqualTo(1)
        assertThat(dashboard.deadLetterCount).isEqualTo(2)
        assertThat(dashboard.recentDeadLetters).hasSize(1)
        assertThat(dashboard.recentDeadLetters.single().eventId).isEqualTo("event-2")
        assertThat(dashboard.recentDeadLetters.single().payload).isEqualTo("{\"applicationId\":2}")
    }

    private fun reader() = RedisAdminNotificationOperationsReader(
        redisTemplate = redisTemplate,
        streamProperties = RedisNotificationStreamProperties(
            streamKey = STREAM_KEY,
            deadLetterStreamKey = DEAD_LETTER_STREAM_KEY,
        ),
        consumerProperties = RedisNotificationStreamConsumerProperties(
            groupName = GROUP_NAME,
            consumerName = "admin-reader-test",
            batchSize = 10,
            pendingMinIdle = Duration.ofMinutes(1),
        ),
    )

    private fun addPendingMessage() {
        redisTemplate.opsForStream<String, String>().add(
            StreamRecords.string(mapOf("payload" to "pending")).withStreamKey(STREAM_KEY),
        )
        redisTemplate.opsForStream<String, String>().createGroup(
            STREAM_KEY,
            ReadOffset.from("0-0"),
            GROUP_NAME,
        )
        redisTemplate.opsForStream<String, String>().read(
            Consumer.from(GROUP_NAME, "worker-a"),
            StreamReadOptions.empty().count(1),
            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
        )
    }

    private fun addDeadLetter(
        eventId: String,
        failedAt: String,
    ) {
        val sequence = eventId.removePrefix("event-")
        redisTemplate.opsForStream<String, String>().add(
            StreamRecords.string(
                mapOf(
                    "sourceRecordId" to "source-$sequence",
                    "eventId" to eventId,
                    "eventType" to "ROOM_APPLICATION_ACCEPTED",
                    "channel" to "EMAIL",
                    "failureType" to "RetryAttemptsExhausted",
                    "originalFailureType" to "EmailDeliveryException",
                    "failureMessage" to "이메일 전송 실패",
                    "attemptCount" to "5",
                    "failedAt" to failedAt,
                    "payload" to "{\"applicationId\":$sequence}",
                ),
            ).withStreamKey(DEAD_LETTER_STREAM_KEY),
        )
    }

    private companion object {
        const val REDIS_PORT = 6379
        const val STREAM_KEY = "admin-notification-dashboard-test"
        const val DEAD_LETTER_STREAM_KEY = "admin-notification-dashboard-dead-letter-test"
        const val GROUP_NAME = "admin-notification-dashboard-group-test"

        @Container
        @JvmStatic
        val redis = AdminNotificationRedisContainer(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(REDIS_PORT)
    }
}

private class AdminNotificationRedisContainer(imageName: DockerImageName) : GenericContainer<AdminNotificationRedisContainer>(imageName)
