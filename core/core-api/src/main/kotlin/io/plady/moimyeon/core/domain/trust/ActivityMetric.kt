package io.plady.moimyeon.core.domain.trust

import java.util.UUID

data class ActivityMetric(
    val attendedCompletedRoomCount: Long,
    val askedQuestionCount: Long = 0,
    val answerSummaryCount: Long = 0,
    val questionFeedbackCount: Long = 0,
) {
    val activityPoint: Long = askedQuestionCount + answerSummaryCount + questionFeedbackCount
}

class ActivityTopPercentCalculator {
    fun calculate(targetMemberId: UUID, metrics: Map<UUID, ActivityMetric>): Int? {
        val target = metrics[targetMemberId]?.takeIf { it.attendedCompletedRoomCount > 0 } ?: return null
        val higherCount = metrics.values.count { metric ->
            metric.activityPoint * target.attendedCompletedRoomCount >
                target.activityPoint * metric.attendedCompletedRoomCount
        }
        val rank = higherCount + 1
        return (rank * 100 + metrics.size - 1) / metrics.size
    }
}
