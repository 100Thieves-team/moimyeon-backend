package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.core.enums.EventType
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
        publisher = RedisNotificationMessagePublisher(
            redisTemplate = redisTemplate,
            properties = RedisNotificationStreamProperties(STREAM_KEY),
        )
    }

    @AfterEach
    fun cleanUp() {
        redisTemplate.delete(STREAM_KEY)
        connectionFactory.destroy()
    }

    @Test
    fun `새 메시지 처리가 성공한 뒤에만 ACK한다`() {
        val eventId = eventId(21)
        publisher.publish(message(eventId))
        val handled = mutableListOf<NotificationStreamMessage>()

        consumer("worker-a").consumeNew { handled += it }

        assertThat(handled.map { it.eventId }).containsExactly(eventId)
        assertThat(pendingCount()).isZero()
    }

    @Test
    fun `메시지 처리에 실패하면 ACK하지 않고 Pending에 남긴다`() {
        publisher.publish(message(eventId(22)))

        consumer("worker-a").consumeNew {
            throw IllegalStateException("외부 전송 실패")
        }

        assertThat(pendingCount()).isEqualTo(1L)
    }

    @Test
    fun `다른 워커의 Pending 메시지를 재선점해 처리하고 ACK한다`() {
        val eventId = eventId(23)
        publisher.publish(message(eventId))
        consumer("worker-a").consumeNew {
            throw IllegalStateException("worker-a 종료")
        }
        val recovered = mutableListOf<NotificationStreamMessage>()

        consumer("worker-b").recoverPending { recovered += it }

        assertThat(recovered.map { it.eventId }).containsExactly(eventId)
        assertThat(pendingCount()).isZero()
    }

    private fun consumer(name: String): NotificationStreamConsumer = RedisNotificationStreamConsumer(
        redisTemplate = redisTemplate,
        properties = RedisNotificationStreamConsumerProperties(
            groupName = GROUP_NAME,
            consumerName = name,
            batchSize = 10,
            pendingMinIdle = Duration.ZERO,
        ),
        streamProperties = RedisNotificationStreamProperties(STREAM_KEY),
    )

    private fun pendingCount(): Long = redisTemplate.opsForStream<String, String>()
        .pending(STREAM_KEY, GROUP_NAME)
        .totalPendingMessages

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
        const val GROUP_NAME = "notification-workers-test"

        @Container
        @JvmStatic
        val redis = ConsumerRedisContainer(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(REDIS_PORT)
    }
}

private class ConsumerRedisContainer(imageName: DockerImageName) : GenericContainer<ConsumerRedisContainer>(imageName)
