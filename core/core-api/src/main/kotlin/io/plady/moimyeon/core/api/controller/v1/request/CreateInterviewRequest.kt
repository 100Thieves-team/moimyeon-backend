package io.plady.moimyeon.core.api.controller.v1.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalTime

// 면접 생성 위저드(B·06~B·09)의 입력을 한 번에 받는다.
// 목킹 단계라 형식 수준 검증만 두고, 카탈로그 참조(companyId/jobRoleId/sigunguId)와
// enum 코드(round/type/method)의 실제 유효성은 도메인 구현 시 검증한다.
data class CreateInterviewRequest(
    // --- B·06 면접 정보 ---
    val companyId: Long,
    // 채용 공고: 회사·직무 카탈로그는 있으나 '공고' 카탈로그 도입 여부가 미확정 → nullable 로 둔다.
    val jobPostingId: Long? = null,
    val jobRoleId: Long,
    val round: String, // FIRST | SECOND | THIRD | FINAL
    val type: String? = null, // JOB | CULTURE_FIT | EXECUTIVE | TECH_ASSIGNMENT (선택)
    // --- B·07 진행 방식과 일정 ---
    val method: String, // ONLINE | OFFLINE
    val sigunguId: Long? = null, // OFFLINE 일 때 지역(구/시·군)
    @field:Min(2)
    val minParticipants: Int, // 방장 포함, 2 이상
    @field:Max(8)
    val maxParticipants: Int, // 8 이하, min<=max
    val schedule: InterviewScheduleRequest,
    // --- B·08 소개와 이력서 ---
    @field:NotBlank
    @field:Size(max = 60)
    val title: String,
    @field:Size(max = 1000)
    val description: String? = null,
    val resumeId: Long,
    val discloseResumeOriginal: Boolean = true,
)

data class InterviewScheduleRequest(
    val date: LocalDate, // 2026-08-01
    val startTime: LocalTime, // 14:00
    val durationMinutes: Int, // 90
)
