package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.mock.MockApiProfile
import io.plady.moimyeon.core.api.controller.v1.request.CreateRoomRequest
import io.plady.moimyeon.core.api.controller.v1.response.CompanyResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobRoleResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomDetailResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomFormOptionsResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomHostResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomHostStatsResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomJobPostingResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomRecruitResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomRecruitSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomRegionResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomScheduleResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomsResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// TODO(룸 생성·탐색): 모킹 응답(고정). 실제 구현 시 @LoginMember 로 방장을 바인딩하고,
// 룸 등록 + 방장 참여 등록을 한 트랜잭션으로 처리하며(「룸 생성」 §4.8, Y1),
// 탐색은 필터/정렬/모집 상태 계산을 도메인에서 처리한다. 회사는 공고에서 파생한다.
@MockApiProfile
@RestController
class RoomController {
    // POST /v1/rooms — 생성 전 확인의 '이대로 룸 만들기'. 생성 즉시 모집(RECRUITING) 상태로 등록(§4.8).
    @PostMapping("/v1/rooms")
    fun create(
        @Valid @RequestBody request: CreateRoomRequest,
    ): ApiResponse<RoomCreatedResponse> {
        return ApiResponse.success(RoomCreatedResponse(roomId = MOCK_ROOM_ID, status = "RECRUITING"))
    }

    // GET /v1/rooms/form-options — 폼 선택지(literal 경로가 {roomId} 보다 우선 매칭됨).
    @GetMapping("/v1/rooms/form-options")
    fun formOptions(): ApiResponse<RoomFormOptionsResponse> {
        return ApiResponse.success(RoomFormOptionsResponse.mock())
    }

    // GET /v1/rooms — 탐색 목록(「룸 탐색」 §4.1~§4.3). 비로그인도 조회 가능. 목은 필터·정렬을 적용하지 않고 고정 목록 반환.
    @GetMapping("/v1/rooms")
    fun list(
        @RequestParam(required = false) companyId: Long?,
        @RequestParam(required = false) jobRoleId: Long?,
        @RequestParam(required = false) round: String?,
        @RequestParam(required = false) method: String?,
        @RequestParam(required = false) sigunguId: Long?,
        @RequestParam(required = false, defaultValue = "false") availableOnly: Boolean,
        @RequestParam(required = false, defaultValue = "SCHEDULE") sort: String,
    ): ApiResponse<RoomsResponse> {
        return ApiResponse.success(mockList(sort))
    }

    // GET /v1/rooms/{roomId} — 생성 완료 및 탐색 상세. 공개 데이터만 노출(§6).
    @GetMapping("/v1/rooms/{roomId}")
    fun detail(
        @PathVariable roomId: UUID,
    ): ApiResponse<RoomDetailResponse> {
        return ApiResponse.success(mockDetail(roomId))
    }

    private fun mockDetail(roomId: UUID): RoomDetailResponse {
        return RoomDetailResponse(
            roomId = roomId,
            status = "RECRUITING",
            statusLabel = "모집 중",
            title = "달빛페이 프론트 1차, 실전처럼 봐요",
            company = CompanyResponse(companyId = 1L, name = "달빛페이"),
            jobPosting = RoomJobPostingResponse(jobPostingId = 1L, postingName = "프론트엔드 개발자 (결제플랫폼)"),
            jobRole = JobRoleResponse(jobRoleId = 1L, code = "FRONTEND_DEVELOPER", displayName = "프론트엔드 개발"),
            round = "FIRST",
            roundLabel = "1차 면접",
            type = "JOB",
            typeLabel = "직무 면접",
            method = "OFFLINE",
            methodLabel = "오프라인",
            region = RoomRegionResponse(sigunguId = 1L, label = "서울 강남구"),
            schedule = RoomScheduleResponse(
                date = LocalDate.of(2026, 8, 1),
                dayOfWeekLabel = "토",
                startTime = LocalTime.of(14, 0),
                startTimeLabel = "오후 2:00",
                durationMinutes = 90,
                durationLabel = "90분",
                displayLabel = "8월 1일 (토) 오후 2:00 · 90분",
            ),
            description = "실제 1차 면접 형식 그대로 진행해요. 한 사람씩 30분 모의면접을 보고, 끝나면 다 같이 피드백을 나눠요. 결제·정산 도메인 질문 위주로 준비할게요.",
            resumePublic = true,
            resumePolicyLabel = "이력서 원본 공개 (진행 확정 후 확정 참여자끼리)",
            notice = "신청할 땐 보관한 이력서를 골라요. 확정 후 취소·노쇼는 활동 이력에 남아요.",
            recruit = RoomRecruitResponse(
                current = 1,
                min = 3,
                max = 6,
                confirmable = false,
                remainingToConfirm = 2,
                progressRatio = 0.17,
                message = "2명이 더 모이면 확정할 수 있어요",
            ),
            host = RoomHostResponse(
                memberId = MOCK_HOST_MEMBER_ID,
                nickname = "집요한 수달 07",
                jobTitle = "프론트엔드 개발",
                isHost = true,
                stats = RoomHostStatsResponse(
                    completedRoomCount = 12,
                    attendanceRate = 96,
                    averageRating = 4.8,
                ),
                aiSummary = "결제 도메인 3년 차 프론트엔드 개발자. React·TypeScript 중심, 디자인 시스템 구축 경험.",
            ),
            viewerRole = "HOST",
        )
    }

    // 요청 sort 를 그대로 되돌려주되(에코), 목록 자체는 고정 2건을 반환한다.
    private fun mockList(sort: String): RoomsResponse {
        return RoomsResponse(
            rooms = listOf(
                RoomSummaryResponse(
                    roomId = MOCK_ROOM_ID,
                    title = "달빛페이 프론트 1차, 실전처럼 봐요",
                    company = CompanyResponse(companyId = 1L, name = "달빛페이"),
                    jobPosting = RoomJobPostingResponse(jobPostingId = 1L, postingName = "프론트엔드 개발자 (결제플랫폼)"),
                    jobRole = JobRoleResponse(jobRoleId = 1L, code = "FRONTEND_DEVELOPER", displayName = "프론트엔드 개발"),
                    round = "FIRST",
                    roundLabel = "1차 면접",
                    type = "JOB",
                    typeLabel = "직무 면접",
                    method = "OFFLINE",
                    methodLabel = "오프라인",
                    region = RoomRegionResponse(sigunguId = 1L, label = "서울 강남구"),
                    schedule = RoomScheduleResponse(
                        date = LocalDate.of(2026, 8, 1),
                        dayOfWeekLabel = "토",
                        startTime = LocalTime.of(14, 0),
                        startTimeLabel = "오후 2:00",
                        durationMinutes = 90,
                        durationLabel = "90분",
                        displayLabel = "8월 1일 (토) 오후 2:00 · 90분",
                    ),
                    recruit = RoomRecruitSummaryResponse(
                        current = 1,
                        max = 6,
                        recruitStatus = "RECRUITING",
                        recruitStatusLabel = "모집 중",
                    ),
                ),
                RoomSummaryResponse(
                    roomId = MOCK_ROOM_ID_2,
                    title = "정산 백엔드 최종 면접 같이 대비해요",
                    company = CompanyResponse(companyId = 1L, name = "달빛페이"),
                    jobPosting = RoomJobPostingResponse(jobPostingId = 2L, postingName = "백엔드 개발자 (정산)"),
                    jobRole = JobRoleResponse(jobRoleId = 2L, code = "BACKEND_DEVELOPER", displayName = "백엔드 개발"),
                    round = "FINAL",
                    roundLabel = "최종 면접",
                    type = null,
                    typeLabel = null,
                    method = "ONLINE",
                    methodLabel = "온라인",
                    region = null,
                    schedule = RoomScheduleResponse(
                        date = LocalDate.of(2026, 8, 3),
                        dayOfWeekLabel = "월",
                        startTime = LocalTime.of(20, 0),
                        startTimeLabel = "오후 8:00",
                        durationMinutes = 60,
                        durationLabel = "60분",
                        displayLabel = "8월 3일 (월) 오후 8:00 · 60분",
                    ),
                    recruit = RoomRecruitSummaryResponse(
                        current = 4,
                        max = 4,
                        recruitStatus = "CLOSED",
                        recruitStatusLabel = "모집 마감",
                    ),
                ),
            ),
            sort = sort,
            totalCount = 2,
        )
    }

    companion object {
        private val MOCK_ROOM_ID: UUID = UUID.fromString("01920000-0000-7000-8000-000000000001")
        private val MOCK_ROOM_ID_2: UUID = UUID.fromString("01920000-0000-7000-8000-000000000002")
        private val MOCK_HOST_MEMBER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
