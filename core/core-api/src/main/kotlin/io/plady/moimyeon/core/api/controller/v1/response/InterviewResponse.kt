package io.plady.moimyeon.core.api.controller.v1.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// 면접 생성(POST /v1/interviews)의 응답. 생성 성공 시 상세로 이동하기 위한 최소 식별자.
data class InterviewCreatedResponse(
    val interviewId: Long,
    val status: String, // RECRUITING
)

// 면접 상세(GET /v1/interviews/{id}) — B·10 생성 완료 화면. 공개 데이터만 노출한다(§6).
// company/jobRole 는 카탈로그 응답 DTO(CompanyResponse·JobRoleResponse)를 재사용한다.
data class InterviewDetailResponse(
    val interviewId: Long,
    val status: String, // RECRUITING
    val statusLabel: String, // 모집 중
    val title: String,
    val company: CompanyResponse,
    val jobPosting: InterviewJobPostingResponse?,
    val jobRole: JobRoleResponse,
    val round: String,
    val roundLabel: String,
    val type: String?,
    val typeLabel: String?,
    val method: String,
    val methodLabel: String,
    val region: InterviewRegionResponse?,
    val schedule: InterviewScheduleResponse,
    val description: String?,
    val notice: String,
    val recruit: RecruitStatusResponse,
    val host: InterviewHostResponse,
    val viewerRole: String, // HOST
)

data class InterviewJobPostingResponse(
    val jobPostingId: Long,
    val title: String, // 프론트엔드 개발자 (결제플랫폼)
)

data class InterviewRegionResponse(
    val sigunguId: Long,
    val label: String, // 서울 강남구
)

data class InterviewScheduleResponse(
    val date: LocalDate,
    val dayOfWeekLabel: String, // 토
    // Jackson 기본 LocalTime 직렬화는 "14:00:00"(ISO) → FE 계약(HH:mm)에 맞춰 "14:00"으로 고정.
    @get:JsonFormat(pattern = "HH:mm")
    val startTime: LocalTime,
    val startTimeLabel: String, // 오후 2:00
    val durationMinutes: Int,
    val durationLabel: String, // 90분
    val displayLabel: String, // 8월 1일 (토) 오후 2:00 · 90분
)

data class RecruitStatusResponse(
    val current: Int, // 1 (방장 겸 참여자)
    val min: Int, // 3
    val max: Int, // 6
    val confirmable: Boolean, // 최소 인원 충족 여부
    val remainingToConfirm: Int, // 2
    val progressRatio: Double, // 0.17
    val message: String, // 2명이 더 모이면 확정할 수 있어요
)

// 방장 블록. GET /v1/members/{memberId}/profile(PublicProfileController 목)의 신뢰 지표와 동일한 값을 미러링한다.
data class InterviewHostResponse(
    val memberId: UUID,
    val nickname: String,
    val jobTitle: String?,
    // Kotlin `isHost` 게터를 Jackson 이 boolean 프로퍼티 "host" 로 오인해 키가 `host` 로 나가는 것을 막고 `isHost` 로 고정.
    @get:JsonProperty("isHost")
    val isHost: Boolean,
    val stats: InterviewHostStatsResponse,
    val aiSummary: String,
)

data class InterviewHostStatsResponse(
    val completedInterviewCount: Int, // 12
    val attendanceRate: Int, // 96
    val averageRating: Double, // 4.8
)
