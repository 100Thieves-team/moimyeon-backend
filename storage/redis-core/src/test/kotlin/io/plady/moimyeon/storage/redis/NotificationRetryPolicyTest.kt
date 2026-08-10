package io.plady.moimyeon.storage.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class NotificationRetryPolicyTest {
    private val policy = NotificationRetryPolicy(
        initialDelay = Duration.ofMinutes(1),
        maxDelay = Duration.ofMinutes(10),
        maxAttempts = 5,
    )

    @Test
    fun `재시도 간격은 시도 횟수마다 두 배가 되고 최대값을 넘지 않는다`() {
        assertThat(policy.delayBeforeAttempt(2)).isEqualTo(Duration.ofMinutes(1))
        assertThat(policy.delayBeforeAttempt(3)).isEqualTo(Duration.ofMinutes(2))
        assertThat(policy.delayBeforeAttempt(4)).isEqualTo(Duration.ofMinutes(4))
        assertThat(policy.delayBeforeAttempt(5)).isEqualTo(Duration.ofMinutes(8))
        assertThat(policy.delayBeforeAttempt(6)).isEqualTo(Duration.ofMinutes(10))
    }

    @Test
    fun `최대 시도 횟수에 도달하면 더 이상 재시도하지 않는다`() {
        assertThat(policy.hasAttemptsRemaining(4)).isTrue()
        assertThat(policy.hasAttemptsRemaining(5)).isFalse()
    }
}
