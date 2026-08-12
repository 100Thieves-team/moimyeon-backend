package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.storage.db.core.AnswerSummaryRepository
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.MemberMetricCount
import io.plady.moimyeon.storage.db.core.QuestionCommentRepository
import io.plady.moimyeon.storage.db.core.QuestionRepository
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class TrustFinder(
    private val attendanceRepository: AttendanceRepository,
    private val questionRepository: QuestionRepository,
    private val answerSummaryRepository: AnswerSummaryRepository,
    private val questionCommentRepository: QuestionCommentRepository,
    private val reviewRepository: ReviewRepository,
    private val clock: Clock,
) {
    private val topPercentCalculator = ActivityTopPercentCalculator()

    @Transactional(readOnly = true)
    fun getPublicTrust(memberId: UUID): PublicTrust {
        val metrics = activityMetrics()
        val recentAttendances = attendanceRepository
            .findRecentCompletedByMember(memberId, PageRequest.of(0, RECENT_ATTENDANCE_LIMIT))
            .map { it.status }
        val noShowCount = attendanceRepository.countCompletedByMemberAndStatus(memberId, AttendanceStatus.ABSENT)
        val representativeTags = reviewRepository
            .findRepresentativeTags(memberId, LocalDateTime.now(clock), PageRequest.of(0, REPRESENTATIVE_TAG_LIMIT))
            .map { RepresentativeTag(label = it.label, count = Math.toIntExact(it.count)) }

        return PublicTrust(
            activityTopPercent = topPercentCalculator.calculate(memberId, metrics),
            recentAttendances = recentAttendances,
            noShowCount = Math.toIntExact(noShowCount),
            representativeTags = representativeTags,
        )
    }

    // TODO: 회원과 완료 룸 규모가 커지면 요청 시 전체 활동 지표 집계를 제거한다.
    // 배치·캐시 또는 반정규화된 회원별 신뢰 통계로 전환하고 공개 프로필은 결과 한 건만 읽게 한다.
    private fun activityMetrics(): Map<UUID, ActivityMetric> {
        val attendedRooms = attendanceRepository.countAttendedCompletedRoomsByMember().toCountMap()
        val askedQuestions = questionRepository.countAskedCompletedActivityByMember().toCountMap()
        val answerSummaries = answerSummaryRepository.countCompletedActivityByMember().toCountMap()
        val questionFeedbacks = questionCommentRepository.countCompletedActivityByMember().toCountMap()

        return attendedRooms.mapValues { (memberId, attendedRoomCount) ->
            ActivityMetric(
                attendedCompletedRoomCount = attendedRoomCount,
                askedQuestionCount = askedQuestions[memberId] ?: 0,
                answerSummaryCount = answerSummaries[memberId] ?: 0,
                questionFeedbackCount = questionFeedbacks[memberId] ?: 0,
            )
        }
    }

    private fun List<MemberMetricCount>.toCountMap(): Map<UUID, Long> = associate { it.memberId to it.count }

    companion object {
        private const val RECENT_ATTENDANCE_LIMIT = 3
        private const val REPRESENTATIVE_TAG_LIMIT = 3
    }
}
