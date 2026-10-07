package io.plady.moimyeon.storage.redis

import java.time.Duration

class NotificationRetryPolicy(
    private val initialDelay: Duration,
    private val maxDelay: Duration,
    private val maxAttempts: Long,
) {
    init {
        require(!initialDelay.isNegative) { "최초 재시도 대기 시간은 음수일 수 없습니다." }
        require(maxDelay >= initialDelay) { "최대 재시도 대기 시간은 최초 대기 시간보다 작을 수 없습니다." }
        require(maxAttempts > 0) { "최대 처리 시도 횟수는 0보다 커야 합니다." }
    }

    fun delayBeforeAttempt(attempt: Long): Duration {
        require(attempt >= 2) { "재시도 대기 시간은 두 번째 처리부터 계산할 수 있습니다." }

        var delay = initialDelay
        repeat((attempt - 2).coerceAtMost(MAX_DOUBLING_COUNT).toInt()) {
            if (delay >= maxDelay.dividedBy(2)) {
                return maxDelay
            }
            delay = delay.multipliedBy(2)
        }
        return delay.coerceAtMost(maxDelay)
    }

    fun hasAttemptsRemaining(completedAttempts: Long): Boolean = completedAttempts < maxAttempts
}

private const val MAX_DOUBLING_COUNT = 62L

private fun Duration.coerceAtMost(maximum: Duration): Duration = if (this <= maximum) this else maximum
