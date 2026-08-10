package io.plady.moimyeon.storage.redis

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationChannel
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Profile("!test")
@ConditionalOnProperty(
    prefix = "notification.worker.consumer",
    name = ["enabled"],
    havingValue = "true",
)
@Component
class NotificationStreamMetrics(
    private val meterRegistry: MeterRegistry,
    properties: RedisNotificationStreamConsumerProperties,
) {
    private val pendingMessages = AtomicLong()
    private val counters = ConcurrentHashMap<MetricKey, Counter>()

    init {
        Gauge.builder(PENDING_MESSAGES_METRIC, pendingMessages) { it.get().toDouble() }
            .description("Redis Stream Consumer Group에서 아직 ACK되지 않은 알림 메시지 수")
            .tag(CONSUMER_GROUP_TAG, properties.groupName)
            .register(meterRegistry)
    }

    fun success(eventType: String?, channel: String?) {
        increment(Outcome.SUCCESS, eventType, channel)
    }

    fun retry(eventType: String?, channel: String?) {
        increment(Outcome.RETRY, eventType, channel)
    }

    fun deadLetter(eventType: String?, channel: String?) {
        increment(Outcome.DEAD_LETTER, eventType, channel)
    }

    fun pending(count: Long) {
        pendingMessages.set(count)
    }

    private fun increment(outcome: Outcome, eventType: String?, channel: String?) {
        val key = MetricKey(
            outcome = outcome.value,
            eventType = eventType.validEventType(),
            channel = channel.validChannel(),
        )
        counters.computeIfAbsent(key) {
            Counter.builder(MESSAGES_METRIC)
                .description("알림 메시지의 Redis 처리 결과")
                .tag(OUTCOME_TAG, it.outcome)
                .tag(EVENT_TYPE_TAG, it.eventType)
                .tag(CHANNEL_TAG, it.channel)
                .register(meterRegistry)
        }.increment()
    }

    private fun String?.validEventType(): String = this
        ?.let { value -> EventType.entries.firstOrNull { it.name == value }?.name }
        ?: UNKNOWN

    private fun String?.validChannel(): String = this
        ?.let { value -> NotificationChannel.entries.firstOrNull { it.name == value }?.name }
        ?: UNKNOWN

    private data class MetricKey(
        val outcome: String,
        val eventType: String,
        val channel: String,
    )

    private enum class Outcome(val value: String) {
        SUCCESS("success"),
        RETRY("retry"),
        DEAD_LETTER("dead_letter"),
    }

    companion object {
        const val MESSAGES_METRIC = "notification.worker.messages"
        const val PENDING_MESSAGES_METRIC = "notification.worker.pending.messages"
        const val OUTCOME_TAG = "outcome"
        const val EVENT_TYPE_TAG = "event_type"
        const val CHANNEL_TAG = "channel"
        const val CONSUMER_GROUP_TAG = "consumer_group"
        const val UNKNOWN = "UNKNOWN"
    }
}
