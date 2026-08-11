package io.plady.moimyeon.storage.redis

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class NotificationStreamMetricsTest {
    @Test
    fun `등록되지 않은 이벤트와 채널 값은 UNKNOWN 라벨로 합친다`() {
        val registry = SimpleMeterRegistry()
        try {
            val metrics = NotificationStreamMetrics(registry, properties())

            metrics.deadLetter("arbitrary-event-id", "arbitrary-channel-id")

            assertThat(
                registry.get(NotificationStreamMetrics.MESSAGES_METRIC)
                    .tag(NotificationStreamMetrics.OUTCOME_TAG, "dead_letter")
                    .tag(NotificationStreamMetrics.EVENT_TYPE_TAG, NotificationStreamMetrics.UNKNOWN)
                    .tag(NotificationStreamMetrics.CHANNEL_TAG, NotificationStreamMetrics.UNKNOWN)
                    .counter()
                    .count(),
            ).isEqualTo(1.0)
        } finally {
            registry.close()
        }
    }

    @Test
    fun `Pending Gauge는 Consumer Group의 마지막 조회 값으로 갱신한다`() {
        val registry = SimpleMeterRegistry()
        try {
            val metrics = NotificationStreamMetrics(registry, properties())

            metrics.pending(17)

            assertThat(
                registry.get(NotificationStreamMetrics.PENDING_MESSAGES_METRIC)
                    .tag(NotificationStreamMetrics.CONSUMER_GROUP_TAG, GROUP_NAME)
                    .gauge()
                    .value(),
            ).isEqualTo(17.0)
        } finally {
            registry.close()
        }
    }

    private fun properties() = RedisNotificationStreamConsumerProperties(
        groupName = GROUP_NAME,
        consumerName = "metrics-test",
        batchSize = 10,
        pendingMinIdle = Duration.ZERO,
    )

    private companion object {
        const val GROUP_NAME = "notification-workers-metrics-test"
    }
}
