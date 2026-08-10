package io.plady.moimyeon.worker.notification.room

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
import io.plady.moimyeon.storage.redis.RedisNotificationStreamConsumer
import io.plady.moimyeon.storage.redis.RedisNotificationStreamConsumerProperties
import io.plady.moimyeon.storage.redis.RedisNotificationStreamProperties
import io.plady.moimyeon.worker.notification.NotificationMessageHandler
import io.plady.moimyeon.worker.notification.NotificationMessageWorker
import io.plady.moimyeon.worker.notification.delivery.Notification
import io.plady.moimyeon.worker.notification.delivery.NotificationContent
import io.plady.moimyeon.worker.notification.delivery.NotificationSender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.time.Duration
import java.util.UUID

@Testcontainers
class RoomApplicationAcceptedNotificationFlowIT {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var notificationSender: RecordingNotificationSender

    @BeforeEach
    fun setUp() {
        connectionFactory = connectionFactory(redis.host, redis.getMappedPort(REDIS_PORT))
        redisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        redisTemplate.delete(STREAM_KEY)
        redisTemplate.delete(DEAD_LETTER_STREAM_KEY)
        notificationSender = RecordingNotificationSender()
    }

    @AfterEach
    fun cleanUp() {
        redisTemplate.delete(STREAM_KEY)
        redisTemplate.delete(DEAD_LETTER_STREAM_KEY)
        connectionFactory.destroy()
    }

    @Test
    fun `참가 신청 수락의 채널별 메시지를 각각 전달하고 ACK한다`() {
        addMessage(NotificationChannel.WEB_PUSH, payload())
        addMessage(NotificationChannel.EMAIL, payload())

        worker().consumeMessages()

        assertThat(notificationSender.notifications).containsExactly(
            Notification(
                eventId = EVENT_ID,
                eventType = EventType.ROOM_APPLICATION_ACCEPTED,
                channel = NotificationChannel.WEB_PUSH,
                recipientMemberId = APPLICANT_ID,
                content = NotificationContent(
                    title = "참가 신청이 수락되었어요",
                    body = "모임에 참여할 수 있게 되었어요.",
                    actionPath = "/rooms/$ROOM_ID",
                ),
            ),
            Notification(
                eventId = EVENT_ID,
                eventType = EventType.ROOM_APPLICATION_ACCEPTED,
                channel = NotificationChannel.EMAIL,
                recipientMemberId = APPLICANT_ID,
                content = NotificationContent(
                    title = "참가 신청이 수락되었어요",
                    body = "모임에 참여할 수 있게 되었어요.",
                    actionPath = "/rooms/$ROOM_ID",
                ),
            ),
        )
        assertThat(pendingCount()).isZero()
    }

    @Test
    fun `payload가 잘못되면 발송하지 않고 DLQ에 격리한다`() {
        addMessage(NotificationChannel.EMAIL, "{invalid-json")

        worker().consumeMessages()

        assertThat(notificationSender.notifications).isEmpty()
        assertThat(pendingCount()).isZero()
        assertThat(deadLetterCount()).isEqualTo(1L)
    }

    @Test
    fun `알림 발송이 실패하면 Stream Pending에 남긴다`() {
        notificationSender.failOnSend = true
        addMessage(NotificationChannel.WEB_PUSH, payload())

        worker().consumeMessages()

        assertThat(pendingCount()).isEqualTo(1L)
    }

    private fun worker(): NotificationMessageWorker {
        val consumer = RedisNotificationStreamConsumer(
            redisTemplate = redisTemplate,
            properties = RedisNotificationStreamConsumerProperties(
                groupName = GROUP_NAME,
                consumerName = "worker-a",
                batchSize = 10,
                pendingMinIdle = Duration.ZERO,
            ),
            streamProperties = RedisNotificationStreamProperties(
                streamKey = STREAM_KEY,
                deadLetterStreamKey = DEAD_LETTER_STREAM_KEY,
            ),
        )
        val handler = NotificationMessageHandler(
            jsonMapper = JsonMapper.builder().addModule(kotlinModule()).build(),
            notificationSender = notificationSender,
        )
        return NotificationMessageWorker(consumer, handler)
    }

    private fun addMessage(
        channel: NotificationChannel,
        payload: String,
    ) {
        redisTemplate.opsForStream<String, String>().add(
            StreamRecords.string(
                mapOf(
                    "eventId" to EVENT_ID.toString(),
                    "eventType" to EventType.ROOM_APPLICATION_ACCEPTED.name,
                    "channel" to channel.name,
                    "payload" to payload,
                ),
            ).withStreamKey(STREAM_KEY),
        )
    }

    private fun pendingCount(): Long = redisTemplate.opsForStream<String, String>()
        .pending(STREAM_KEY, GROUP_NAME)
        .totalPendingMessages

    private fun deadLetterCount(): Long = redisTemplate.opsForStream<String, String>()
        .size(DEAD_LETTER_STREAM_KEY)

    private fun payload() =
        """
        {
          "eventId": "$EVENT_ID",
          "eventType": "ROOM_APPLICATION_ACCEPTED",
          "applicationId": 1,
          "roomId": "$ROOM_ID",
          "applicantMemberId": "$APPLICANT_ID"
        }
        """.trimIndent()

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
        const val STREAM_KEY = "room-application-accepted-flow-test"
        const val DEAD_LETTER_STREAM_KEY = "room-application-accepted-dead-letter-flow-test"
        const val GROUP_NAME = "notification-workers-flow-test"
        val EVENT_ID: UUID = UUID.fromString("0198b4f4-2f00-7000-8000-000000000001")
        val ROOM_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val APPLICANT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

        @Container
        @JvmStatic
        val redis = WorkerNotificationRedisContainer(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(REDIS_PORT)
    }
}

private class RecordingNotificationSender : NotificationSender {
    val notifications = mutableListOf<Notification>()
    var failOnSend: Boolean = false

    override fun send(notification: Notification) {
        if (failOnSend) {
            throw IllegalStateException("알림 발송 실패")
        }
        notifications += notification
    }
}

private class WorkerNotificationRedisContainer(imageName: DockerImageName) : GenericContainer<WorkerNotificationRedisContainer>(imageName)
