package io.plady.moimyeon.core.domain.trust

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ActivityTopPercentCalculatorTest {
    private val calculator = ActivityTopPercentCalculator()
    private val targetMemberId = UUID.randomUUID()

    @Test
    fun `질문 꼬리질문 답변 요약 질문 피드백을 각각 1점으로 계산한다`() {
        val metric = ActivityMetric(
            attendedCompletedRoomCount = 2,
            askedQuestionCount = 2,
            answerSummaryCount = 1,
            questionFeedbackCount = 3,
        )

        assertThat(metric.activityPoint).isEqualTo(6)
    }

    @Test
    fun `활동 점수를 출석한 완료 룸 수로 나눈 평균을 비교한다`() {
        val higherAverageMemberId = UUID.randomUUID()
        val metrics = mapOf(
            targetMemberId to ActivityMetric(attendedCompletedRoomCount = 2, askedQuestionCount = 4),
            higherAverageMemberId to ActivityMetric(attendedCompletedRoomCount = 1, askedQuestionCount = 3),
        )

        assertThat(calculator.calculate(targetMemberId, metrics)).isEqualTo(100)
    }

    @Test
    fun `동률 회원은 같은 순위와 상위 퍼센트를 가진다`() {
        val tiedMemberId = UUID.randomUUID()
        val higherAverageMemberId = UUID.randomUUID()
        val metrics = mapOf(
            targetMemberId to ActivityMetric(attendedCompletedRoomCount = 1, askedQuestionCount = 2),
            tiedMemberId to ActivityMetric(attendedCompletedRoomCount = 2, askedQuestionCount = 4),
            higherAverageMemberId to ActivityMetric(attendedCompletedRoomCount = 1, askedQuestionCount = 3),
        )

        assertThat(calculator.calculate(targetMemberId, metrics)).isEqualTo(67)
        assertThat(calculator.calculate(tiedMemberId, metrics)).isEqualTo(67)
    }

    @Test
    fun `상위 퍼센트는 순위 곱하기 100을 모수로 나눈 값을 올림한다`() {
        val metrics = mapOf(
            targetMemberId to ActivityMetric(attendedCompletedRoomCount = 1, askedQuestionCount = 2),
            UUID.randomUUID() to ActivityMetric(attendedCompletedRoomCount = 1, askedQuestionCount = 3),
            UUID.randomUUID() to ActivityMetric(attendedCompletedRoomCount = 1, askedQuestionCount = 1),
        )

        assertThat(calculator.calculate(targetMemberId, metrics)).isEqualTo(67)
    }

    @Test
    fun `출석한 완료 룸이 없으면 활동 상위 퍼센트는 null 이다`() {
        val metrics = mapOf(
            UUID.randomUUID() to ActivityMetric(attendedCompletedRoomCount = 1, askedQuestionCount = 1),
        )

        assertThat(calculator.calculate(targetMemberId, metrics)).isNull()
    }
}
