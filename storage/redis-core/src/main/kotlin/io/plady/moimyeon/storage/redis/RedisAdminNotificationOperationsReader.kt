package io.plady.moimyeon.storage.redis

import io.plady.moimyeon.admin.notification.AdminDeadLetterMessage
import io.plady.moimyeon.admin.notification.AdminNotificationDashboard
import io.plady.moimyeon.admin.notification.AdminNotificationOperationsReader
import org.springframework.context.annotation.Profile
import org.springframework.dao.DataAccessException
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.Limit
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Profile("!test")
@Component
class RedisAdminNotificationOperationsReader(
    private val redisTemplate: StringRedisTemplate,
    private val streamProperties: RedisNotificationStreamProperties,
    private val consumerProperties: RedisNotificationStreamConsumerProperties,
) : AdminNotificationOperationsReader {
    override fun loadDashboard(recentDeadLetterLimit: Int): AdminNotificationDashboard {
        require(recentDeadLetterLimit > 0) { "최근 DLQ 조회 개수는 0보다 커야 합니다." }

        return AdminNotificationDashboard(
            pendingCount = pendingCount(),
            deadLetterCount = streamOperations.size(streamProperties.deadLetterStreamKey) ?: 0,
            recentDeadLetters = streamOperations.reverseRange(
                streamProperties.deadLetterStreamKey,
                Range.unbounded(),
                Limit.limit().count(recentDeadLetterLimit),
            ).map { it.toDeadLetterMessage() },
        )
    }

    private fun pendingCount(): Long = try {
        streamOperations.pending(
            streamProperties.streamKey,
            consumerProperties.groupName,
        ).totalPendingMessages
    } catch (exception: DataAccessException) {
        if (exception.hasNoGroupCause()) 0 else throw exception
    }

    private fun MapRecord<String, String, String>.toDeadLetterMessage() = AdminDeadLetterMessage(
        recordId = id.value,
        sourceRecordId = value[SOURCE_RECORD_ID].orEmpty(),
        eventId = value[EVENT_ID].orEmpty(),
        eventType = value[EVENT_TYPE].orEmpty(),
        channel = value[CHANNEL].orEmpty(),
        failureType = value[FAILURE_TYPE].orEmpty(),
        originalFailureType = value[ORIGINAL_FAILURE_TYPE].orEmpty(),
        failureMessage = value[FAILURE_MESSAGE].orEmpty(),
        attemptCount = value[ATTEMPT_COUNT].orEmpty(),
        failedAt = value[FAILED_AT].orEmpty(),
        payload = value[PAYLOAD].orEmpty(),
    )

    private fun Throwable.hasNoGroupCause(): Boolean = generateSequence(this) { it.cause }
        .any { it.message?.contains("NOGROUP") == true }

    private val streamOperations
        get() = redisTemplate.opsForStream<String, String>()

    private companion object {
        const val SOURCE_RECORD_ID = "sourceRecordId"
        const val EVENT_ID = "eventId"
        const val EVENT_TYPE = "eventType"
        const val CHANNEL = "channel"
        const val FAILURE_TYPE = "failureType"
        const val ORIGINAL_FAILURE_TYPE = "originalFailureType"
        const val FAILURE_MESSAGE = "failureMessage"
        const val ATTEMPT_COUNT = "attemptCount"
        const val FAILED_AT = "failedAt"
        const val PAYLOAD = "payload"
    }
}
