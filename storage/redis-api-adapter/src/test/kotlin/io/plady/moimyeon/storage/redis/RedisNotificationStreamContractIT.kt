package io.plady.moimyeon.storage.redis

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
import io.plady.moimyeon.core.enums.NotificationPolicy
import io.plady.moimyeon.core.notification.OutgoingNotification
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

// 발행·운영 조회(redis-api-adapter)와 소비·DLQ 기록(redis-core)이 같은 Stream 필드 형식을 쓰는지 확인한다(MOI-591).
@Testcontainers
class RedisNotificationStreamContractIT {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setUp() {
        connectionFactory = connectionFactory(redis.host, redis.getMappedPort(REDIS_PORT))
        redisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        redisTemplate.delete(listOf(STREAM_KEY, DEAD_LETTER_STREAM_KEY))
        meterRegistry = SimpleMeterRegistry()
    }

    @AfterEach
    fun cleanUp() {
        redisTemplate.delete(listOf(STREAM_KEY, DEAD_LETTER_STREAM_KEY))
        meterRegistry.close()
        connectionFactory.destroy()
    }

    @Test
    fun `발행기가 기록한 채널 메시지를 소비자가 같은 값으로 읽는다`() {
        val eventId = UUID.fromString("0198b4f4-2f00-7000-8000-000000000031")
        val payload = "{\"eventId\":\"$eventId\"}"
        RedisNotificationMessagePublisher(redisTemplate, streamProperties)
            .publish(listOf(notification(eventId, payload)))
        val consumed = mutableListOf<NotificationStreamMessage>()

        consumer().consumeNew {
            consumed += it
            NotificationStreamHandlingResult.success()
        }

        assertThat(consumed).containsExactly(
            NotificationStreamMessage(eventId, EventType.ROOM_APPLICATION_ACCEPTED.name, NotificationChannel.WEB_PUSH, payload),
            NotificationStreamMessage(eventId, EventType.ROOM_APPLICATION_ACCEPTED.name, NotificationChannel.EMAIL, payload),
        )
    }

    @Test
    fun `소비자가 DLQ에 기록한 메시지를 운영 조회가 같은 값으로 읽는다`() {
        val eventId = UUID.fromString("0198b4f4-2f00-7000-8000-000000000032")
        val payload = "{\"eventId\":\"$eventId\"}"
        RedisNotificationMessagePublisher(redisTemplate, streamProperties)
            .publish(listOf(notification(eventId, payload)))

        consumer().consumeNew {
            NotificationStreamHandlingResult.permanentFailure("InvalidPayload", "payload 불일치")
        }
        val dashboard = RedisAdminNotificationOperationsReader(redisTemplate, streamProperties, consumerProperties)
            .loadDashboard(recentDeadLetterLimit = 10)

        assertThat(dashboard.deadLetterCount).isEqualTo(2)
        dashboard.recentDeadLetters.forEach { deadLetter ->
            assertThat(deadLetter.sourceRecordId).isNotBlank()
            assertThat(deadLetter.eventId).isEqualTo(eventId.toString())
            assertThat(deadLetter.eventType).isEqualTo(EventType.ROOM_APPLICATION_ACCEPTED.name)
            assertThat(deadLetter.failureType).isEqualTo("InvalidPayload")
            assertThat(deadLetter.failureMessage).isEqualTo("payload 불일치")
            assertThat(deadLetter.attemptCount).isEqualTo("1")
            assertThat(deadLetter.failedAt).isNotBlank()
            assertThat(deadLetter.payload).isEqualTo(payload)
        }
        assertThat(dashboard.recentDeadLetters.map { it.channel })
            .containsExactlyInAnyOrder(NotificationChannel.WEB_PUSH.name, NotificationChannel.EMAIL.name)
    }

    private val streamProperties = RedisNotificationStreamProperties(
        streamKey = STREAM_KEY,
        deadLetterStreamKey = DEAD_LETTER_STREAM_KEY,
    )

    private val consumerProperties = RedisNotificationStreamConsumerProperties(
        groupName = GROUP_NAME,
        consumerName = "contract-worker",
        batchSize = 10,
        pendingMinIdle = Duration.ZERO,
    )

    private fun consumer(): NotificationStreamConsumer = RedisNotificationStreamConsumer(
        redisTemplate = redisTemplate,
        properties = consumerProperties,
        streamProperties = streamProperties,
        metrics = NotificationStreamMetrics(meterRegistry, consumerProperties),
    )

    private fun notification(eventId: UUID, payload: String) = OutgoingNotification(
        eventId = eventId,
        eventType = EventType.ROOM_APPLICATION_ACCEPTED,
        policy = NotificationPolicy.PUSH_AND_EMAIL,
        payload = payload,
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
        const val STREAM_KEY = "notification-events-contract-test"
        const val DEAD_LETTER_STREAM_KEY = "notification-events-dead-letter-contract-test"
        const val GROUP_NAME = "notification-workers-contract-test"

        @Container
        @JvmStatic
        val redis = ContractRedisContainer(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(REDIS_PORT)
    }
}

private class ContractRedisContainer(imageName: DockerImageName) : GenericContainer<ContractRedisContainer>(imageName)
