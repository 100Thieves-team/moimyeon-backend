package io.plady.moimyeon.storage.redis

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile
import java.time.Duration

@ConfigurationProperties("storage.redis.notification.consumer")
@Profile("!test")
data class RedisNotificationStreamConsumerProperties(
    val groupName: String,
    val consumerName: String,
    val batchSize: Int,
    val pendingMinIdle: Duration,
) {
    init {
        require(groupName.isNotBlank()) { "Redis Stream Consumer Group 이름은 비어 있을 수 없습니다." }
        require(consumerName.isNotBlank()) { "Redis Stream Consumer 이름은 비어 있을 수 없습니다." }
        require(batchSize > 0) { "Redis Stream Consumer 배치 크기는 0보다 커야 합니다." }
        require(!pendingMinIdle.isNegative) { "Pending 최소 대기 시간은 음수일 수 없습니다." }
    }
}
