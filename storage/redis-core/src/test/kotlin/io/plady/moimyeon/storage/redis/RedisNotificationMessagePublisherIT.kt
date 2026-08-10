package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
import io.plady.moimyeon.core.notification.outbox.RelayMessage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Range
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Testcontainers
class RedisNotificationMessagePublisherIT {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun setUp() {
        connectionFactory = connectionFactory(redis.host, redis.getMappedPort(REDIS_PORT))
        redisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        redisTemplate.delete(STREAM_KEY)
        redisTemplate.delete(OUTBOX_RELAY_LOCK_KEY)
    }

    @AfterEach
    fun cleanUp() {
        redisTemplate.delete(STREAM_KEY)
        redisTemplate.delete(OUTBOX_RELAY_LOCK_KEY)
        connectionFactory.destroy()
    }

    @Test
    fun `RelayMessage를 이벤트 정책의 채널별 메시지로 한 번에 저장한다`() {
        val publisher = RedisNotificationMessagePublisher(
            redisTemplate = redisTemplate,
            properties = RedisNotificationStreamProperties(STREAM_KEY),
        )

        publisher.publish(
            RelayMessage(
                eventId = EVENT_ID,
                eventType = EventType.ROOM_APPLICATION_ACCEPTED,
                payload = "{\"applicationId\":1}",
            ),
        )

        val records = redisTemplate.opsForStream<String, String>().range(STREAM_KEY, Range.unbounded())
        assertThat(records).hasSize(2)
        assertThat(records.map { it.value["channel"] }).containsExactly(
            NotificationChannel.WEB_PUSH.name,
            NotificationChannel.EMAIL.name,
        )
        records.forEach { record ->
            assertThat(record.value)
                .containsEntry("eventId", EVENT_ID.toString())
                .containsEntry("eventType", "ROOM_APPLICATION_ACCEPTED")
                .containsEntry("payload", "{\"applicationId\":1}")
        }
    }

    @Test
    fun `Redis가 메시지를 저장하지 못하면 실패를 호출자에게 전달한다`() {
        val unavailableConnectionFactory = connectionFactory("127.0.0.1", unusedPort())
        val unavailableRedisTemplate = StringRedisTemplate(unavailableConnectionFactory).apply { afterPropertiesSet() }
        val publisher = RedisNotificationMessagePublisher(
            redisTemplate = unavailableRedisTemplate,
            properties = RedisNotificationStreamProperties(STREAM_KEY),
        )

        try {
            assertThatThrownBy {
                publisher.publish(
                    RelayMessage(
                        eventId = EVENT_ID,
                        eventType = EventType.ROOM_APPLICATION_ACCEPTED,
                        payload = "{\"applicationId\":2}",
                    ),
                )
            }.isInstanceOf(RedisConnectionFailureException::class.java)
        } finally {
            unavailableConnectionFactory.destroy()
        }
    }

    @Test
    fun `두 미처리 Outbox 재전달이 같은 락을 경쟁하면 한 곳만 실행한다`() {
        val properties = RedisOutboxRelayProperties(
            lockKey = OUTBOX_RELAY_LOCK_KEY,
            lockDuration = Duration.ofSeconds(10),
        )
        val firstCoordinator = RedisOutboxRelayCoordinator(redisTemplate, properties)
        val secondCoordinator = RedisOutboxRelayCoordinator(redisTemplate, properties)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondExecuted = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val firstResult = executor.submit<Boolean> {
                firstCoordinator.relayPendingIfAvailable {
                    firstStarted.countDown()
                    releaseFirst.await(2, TimeUnit.SECONDS)
                }
            }
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue()

            val secondResult = secondCoordinator.relayPendingIfAvailable {
                secondExecuted.set(true)
            }

            assertThat(secondResult).isFalse()
            assertThat(secondExecuted.get()).isFalse()
            releaseFirst.countDown()
            assertThat(firstResult.get(2, TimeUnit.SECONDS)).isTrue()

            val executedAfterRelease = AtomicBoolean(false)
            val acquiredAfterRelease = secondCoordinator.relayPendingIfAvailable {
                executedAfterRelease.set(true)
            }
            assertThat(acquiredAfterRelease).isTrue()
            assertThat(executedAfterRelease.get()).isTrue()
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `Redis에서 실행 권한을 확인할 수 없으면 미처리 Outbox 재전달을 시작하지 않는다`() {
        val unavailableConnectionFactory = connectionFactory("127.0.0.1", unusedPort())
        val unavailableRedisTemplate = StringRedisTemplate(unavailableConnectionFactory).apply { afterPropertiesSet() }
        val coordinator = RedisOutboxRelayCoordinator(
            redisTemplate = unavailableRedisTemplate,
            properties = RedisOutboxRelayProperties(
                lockKey = OUTBOX_RELAY_LOCK_KEY,
                lockDuration = Duration.ofSeconds(10),
            ),
        )
        val executed = AtomicBoolean(false)

        try {
            assertThatThrownBy {
                coordinator.relayPendingIfAvailable {
                    executed.set(true)
                }
            }.isInstanceOf(RedisConnectionFailureException::class.java)
            assertThat(executed.get()).isFalse()
        } finally {
            unavailableConnectionFactory.destroy()
        }
    }

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

    private fun unusedPort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        const val REDIS_PORT = 6379
        const val STREAM_KEY = "notification-events-test"
        const val OUTBOX_RELAY_LOCK_KEY = "notification-outbox-relay-test"
        val EVENT_ID: UUID = UUID.fromString("0198b4f4-2f00-7000-8000-000000000017")

        @Container
        @JvmStatic
        val redis = RedisContainer(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(REDIS_PORT)
    }
}

private class RedisContainer(imageName: DockerImageName) : GenericContainer<RedisContainer>(imageName)
