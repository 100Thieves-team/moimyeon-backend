package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CreateRoomRequest
import io.plady.moimyeon.core.api.controller.v1.request.UpdateRoomRequest
import io.plady.moimyeon.core.api.facade.RoomFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.api.controller.v1.response.CompanyResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobRoleResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomFormOptionsResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomJobPostingResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomReadResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomRecruitSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomRegionResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomScheduleResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomsResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID


@RestController
class RoomController(
    private val roomFacade: RoomFacade,
) {
    // POST /v1/rooms — 생성 전 확인의 '이대로 룸 만들기'. 생성 즉시 모집(RECRUITING) 상태로 등록(§4.8).
    @PostMapping("/v1/rooms")
    fun create(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: CreateRoomRequest,
    ): ApiResponse<RoomCreatedResponse> {
        return ApiResponse.success(roomFacade.create(currentMember.id, request.toCommand()))
    }

    // PUT /v1/rooms/{roomId} — 방장이 생성 이후 편집 가능한 정보를 수정한다(모집중 상태 편집). 방장만 가능.
    @PutMapping("/v1/rooms/{roomId}")
    fun update(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @RequestBody request: UpdateRoomRequest,
    ): ApiResponse<Any> {
        roomFacade.update(currentMember.id, roomId, request.toCommand())
        return ApiResponse.success()
    }

    // DELETE /v1/rooms/{roomId} — 방장이 룸을 삭제(소프트 삭제)한다. 방장만 가능.
    @DeleteMapping("/v1/rooms/{roomId}")
    fun delete(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<Any> {
        roomFacade.delete(currentMember.id, roomId)
        return ApiResponse.success()
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

    // GET /v1/rooms/{roomId} — 룸 단건 조회. 룸의 실제 저장 데이터 + 현재 인원 + 방장 식별자를 반환한다.
    // 회사·공고·직무 표시명, 방장 프로필/신뢰 지표, 탐색 목록 enrich 는 별도 이슈다(docs/room-progress.md).
    @GetMapping("/v1/rooms/{roomId}")
    fun detail(
        @PathVariable roomId: UUID,
    ): ApiResponse<RoomReadResponse> {
        return ApiResponse.success(roomFacade.getRoom(roomId))
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
    }
}
