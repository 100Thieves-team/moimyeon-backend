package io.plady.moimyeon.core.domain.resume

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class ResumeSummaryDeadlineTest {
    @Test
    fun `45초 예산에서 20초 호출은 25초까지 시작할 수 있다`() {
        val deadline = ResumeSummaryDeadline.start(0L)

        assertThat(deadline.hasTimeFor(Duration.ofSeconds(20), Duration.ofSeconds(25).toNanos())).isTrue()
        assertThat(deadline.hasTimeFor(Duration.ofSeconds(20), Duration.ofSeconds(25).toNanos() + 1)).isFalse()
    }
}
