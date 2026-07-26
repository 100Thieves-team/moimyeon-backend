package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.mock.MockApiProfile
import io.plady.moimyeon.core.api.controller.v1.request.CreateInterviewRequest
import io.plady.moimyeon.core.api.controller.v1.response.CompanyResponse
import io.plady.moimyeon.core.api.controller.v1.response.InterviewCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.InterviewDetailResponse
import io.plady.moimyeon.core.api.controller.v1.response.InterviewFormOptionsResponse
import io.plady.moimyeon.core.api.controller.v1.response.InterviewHostResponse
import io.plady.moimyeon.core.api.controller.v1.response.InterviewHostStatsResponse
import io.plady.moimyeon.core.api.controller.v1.response.InterviewJobPostingResponse
import io.plady.moimyeon.core.api.controller.v1.response.InterviewRegionResponse
import io.plady.moimyeon.core.api.controller.v1.response.InterviewScheduleResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobRoleResponse
import io.plady.moimyeon.core.api.controller.v1.response.RecruitStatusResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// TODO(면접 생성): 모킹 응답(고정). 실제 구현 시 @LoginMember 로 방장을 바인딩하고,
// 카탈로그/일정/인원 규칙(생성 §4.1~§4.4)과 영속화·상태(RECRUITING) 전이를 도메인에서 처리한다.
// 채용 공고(jobPosting)는 카탈로그 도입 여부 확정 후 실데이터로 교체한다.
@MockApiProfile
@RestController
class InterviewController {
    // POST /v1/interviews — 생성 전 확인(B·09)의 '이대로 면접 만들기'. 생성 즉시 모집 상태로 등록(§4.8).
    @PostMapping("/v1/interviews")
    fun create(
        @Valid @RequestBody request: CreateInterviewRequest,
    ): ApiResponse<InterviewCreatedResponse> {
        return ApiResponse.success(InterviewCreatedResponse(interviewId = MOCK_INTERVIEW_ID, status = "RECRUITING"))
    }

    // GET /v1/interviews/form-options — 폼 선택지(literal 경로가 {interviewId} 보다 우선 매칭됨).
    @GetMapping("/v1/interviews/form-options")
    fun formOptions(): ApiResponse<InterviewFormOptionsResponse> {
        return ApiResponse.success(InterviewFormOptionsResponse.mock())
    }

    // GET /v1/interviews/{interviewId} — 생성 완료 상세(B·10). 공개 데이터만 노출(§6).
    @GetMapping("/v1/interviews/{interviewId}")
    fun detail(
        @PathVariable interviewId: Long,
    ): ApiResponse<InterviewDetailResponse> {
        return ApiResponse.success(mockDetail(interviewId))
    }

    private fun mockDetail(interviewId: Long): InterviewDetailResponse {
        return InterviewDetailResponse(
            interviewId = interviewId,
            status = "RECRUITING",
            statusLabel = "모집 중",
            title = "달빛페이 프론트 1차, 실전처럼 봐요",
            company = CompanyResponse(companyId = 1L, name = "달빛페이"),
            jobPosting = InterviewJobPostingResponse(jobPostingId = 1L, title = "프론트엔드 개발자 (결제플랫폼)"),
            jobRole = JobRoleResponse(jobRoleId = 1L, code = "FRONTEND_DEVELOPER", displayName = "프론트엔드 개발"),
            round = "FIRST",
            roundLabel = "1차 면접",
            type = "JOB",
            typeLabel = "직무 면접",
            method = "OFFLINE",
            methodLabel = "오프라인",
            region = InterviewRegionResponse(sigunguId = 1L, label = "서울 강남구"),
            schedule = InterviewScheduleResponse(
                date = LocalDate.of(2026, 8, 1),
                dayOfWeekLabel = "토",
                startTime = LocalTime.of(14, 0),
                startTimeLabel = "오후 2:00",
                durationMinutes = 90,
                durationLabel = "90분",
                displayLabel = "8월 1일 (토) 오후 2:00 · 90분",
            ),
            description = "실제 1차 면접 형식 그대로 진행해요. 한 사람씩 30분 모의면접을 보고, 끝나면 다 같이 피드백을 나눠요. 결제·정산 도메인 질문 위주로 준비할게요.",
            notice = "신청할 땐 이력서를 올려요. 확정 후 취소·노쇼는 활동 이력에 남아요.",
            recruit = RecruitStatusResponse(
                current = 1,
                min = 3,
                max = 6,
                confirmable = false,
                remainingToConfirm = 2,
                progressRatio = 0.17,
                message = "2명이 더 모이면 확정할 수 있어요",
            ),
            host = InterviewHostResponse(
                memberId = MOCK_HOST_MEMBER_ID,
                nickname = "집요한 수달 07",
                jobTitle = "프론트엔드 개발",
                isHost = true,
                stats = InterviewHostStatsResponse(
                    completedInterviewCount = 12,
                    attendanceRate = 96,
                    averageRating = 4.8,
                ),
                aiSummary = "결제 도메인 3년 차 프론트엔드 개발자. React·TypeScript 중심, 디자인 시스템 구축 경험.",
            ),
            viewerRole = "HOST",
        )
    }

    companion object {
        private const val MOCK_INTERVIEW_ID = 1024L
        private val MOCK_HOST_MEMBER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
