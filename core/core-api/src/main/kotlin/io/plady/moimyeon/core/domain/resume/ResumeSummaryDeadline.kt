package io.plady.moimyeon.core.domain.resume

import java.time.Duration

class ResumeSummaryDeadline private constructor(
    private val startedAtNanos: Long,
    private val totalBudgetNanos: Long,
) {
    fun hasTimeFor(requiredDuration: Duration, currentTimeNanos: Long): Boolean {
        val remaining = remainingDuration(currentTimeNanos)
        return !remaining.isZero && remaining >= requiredDuration
    }

    fun remainingDuration(currentTimeNanos: Long): Duration {
        val elapsedNanos = currentTimeNanos - startedAtNanos
        if (elapsedNanos < 0 || elapsedNanos >= totalBudgetNanos) {
            return Duration.ZERO
        }
        return Duration.ofNanos(totalBudgetNanos - elapsedNanos)
    }

    companion object {
        fun start(currentTimeNanos: Long): ResumeSummaryDeadline {
            return ResumeSummaryDeadline(currentTimeNanos, TOTAL_BUDGET.toNanos())
        }

        private val TOTAL_BUDGET = Duration.ofSeconds(45)
    }
}

fun interface ResumeSummaryTimeSource {
    fun nanoTime(): Long
}
