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
            // 평균 나눗셈의 부동소수점 오차가 동률 순위를 바꾸지 않도록 출석 수를 교차 곱한다.
            metric.activityPoint * target.attendedCompletedRoomCount >
                target.activityPoint * metric.attendedCompletedRoomCount
        }
        val rank = higherCount + 1
        // 양의 정수 나눗셈으로 ceil(rank * 100 / 활동회원 모수)를 계산한다.
        return (rank * 100 + metrics.size - 1) / metrics.size
    }
}
