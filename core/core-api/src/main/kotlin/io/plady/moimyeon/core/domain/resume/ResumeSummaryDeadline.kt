package io.plady.moimyeon.core.domain.resume

import java.time.Duration

class ResumeSummaryDeadline private constructor(
    private val startedAtNanos: Long,
    private val totalBudgetNanos: Long,
) {
    fun hasTimeFor(requiredDuration: Duration, currentTimeNanos: Long): Boolean {
        val elapsedNanos = currentTimeNanos - startedAtNanos
        val latestStartNanos = totalBudgetNanos - requiredDuration.toNanos()
        return elapsedNanos >= 0 && latestStartNanos >= 0 && elapsedNanos <= latestStartNanos
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
