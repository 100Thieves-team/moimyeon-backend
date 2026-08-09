package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.MeetingType
import java.time.LocalDateTime

// 탐색 필터(「룸 탐색」 §4.2). 처음부터 조건 객체로 받아 필터가 늘어도 호출부와 시그니처가 그대로다.
//
// 값 규칙(조회 범위의 앞뒤 관계 등)은 여기서 판정하지 않는다 — 요청 DTO 가 확정하고 넘긴다.
// 잘못된 enum 값도 여기 도달하기 전에 null 로 떨어진다. 존재하지 않는 식별자는 무시하지 않는다:
// 그대로 조건에 걸어 결과가 0건이 되게 두고, 대체 추천은 별도 API 가 맡는다.
data class RoomSearchCondition(
    val companyId: Long?,
    val jobPostingId: Long?,
    val jobRoleId: Long?,
    val interviewStage: InterviewStage?,
    val meetingType: MeetingType?,
    val sigunguId: Long?,
    val startFrom: LocalDateTime?,
    val startTo: LocalDateTime?,
    val availableOnly: Boolean,
) {
    /**
     * 회사·공고 필터를 조회에 쓸 공고 id 목록으로 확정한다.
     *
     * 룸은 회사를 직접 알지 못하므로(room → job_posting → company) 회사 필터는 공고 id 목록으로 바꿔야 한다.
     * 그 조회는 이 객체가 하지 않고 호출자가 넘긴다.
     *
     * - null 을 반환하면 "공고로 좁히지 않는다"는 뜻이다.
     * - 빈 목록을 반환하면 "좁힌 결과가 없다"는 뜻이라 조회할 필요조차 없다. 호출자가 조기 반환한다.
     */
    fun resolveJobPostingTargets(companyPostingIds: List<Long>?): List<Long>? = when {
        companyPostingIds == null -> jobPostingId?.let { listOf(it) }
        jobPostingId == null -> companyPostingIds
        else -> companyPostingIds.filter { it == jobPostingId }
    }

    companion object {
        val EMPTY = RoomSearchCondition(
            companyId = null,
            jobPostingId = null,
            jobRoleId = null,
            interviewStage = null,
            meetingType = null,
            sigunguId = null,
            startFrom = null,
            startTo = null,
            availableOnly = false,
        )
    }
}
