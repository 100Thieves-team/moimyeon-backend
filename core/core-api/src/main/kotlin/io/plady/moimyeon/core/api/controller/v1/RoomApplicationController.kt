package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.mock.MockApiProfile
import io.plady.moimyeon.core.api.controller.v1.request.RejectApplicationRequest
import io.plady.moimyeon.core.api.controller.v1.response.ApplicantResponse
import io.plady.moimyeon.core.api.controller.v1.response.ApplicationAiSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.ApplicationDecisionResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationsResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomRecruitSummaryResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.util.UUID

// TODO(참가 신청 관리): 모킹 응답(고정). 실제 구현 시 방장 권한 검사(§4.3),
// 수락 시점 정원 동시성 제어(마지막 자리 1건만 성공, §4.4·Y3), 참여자 등록·모집 상태 계산을 도메인에서 처리한다.
// 신청 목록 조회(§4.3)는 이력서 AI 요약 조인을 포함한다(J5).
@MockApiProfile
@RestController
class RoomApplicationController {
    // GET /v1/rooms/{roomId}/applications — 방장용 참가 신청 목록(§4.3). 전달 사항·AI 요약은 방장 외 비공개.
    @GetMapping("/v1/rooms/{roomId}/applications")
    fun applications(
        @PathVariable roomId: UUID,
    ): ApiResponse<RoomApplicationsResponse> {
        return ApiResponse.success(mockApplications())
    }

    // POST /v1/rooms/{roomId}/applications/{applicationId}/accept — 신청 수락(§4.4).
    // 서버가 정원을 최종 확인한 뒤 참여자로 등록하고 현재 인원을 증가시킨다. 목은 성공 결과(current+1)를 고정 반환.
    @PostMapping("/v1/rooms/{roomId}/applications/{applicationId}/accept")
    fun accept(
        @PathVariable roomId: UUID,
        @PathVariable applicationId: Long,
    ): ApiResponse<ApplicationDecisionResponse> {
        return ApiResponse.success(
            ApplicationDecisionResponse(
                applicationId = applicationId,
                status = "ACCEPTED",
                statusLabel = "수락",
                recruit = RoomRecruitSummaryResponse(
                    current = 2,
                    max = 6,
                    recruitStatus = "RECRUITING",
                    recruitStatusLabel = "모집 중",
                ),
            ),
        )
    }

    // POST /v1/rooms/{roomId}/applications/{applicationId}/reject — 신청 반려(§4.4). 사유는 선택.
    // 반려는 정원·참여자 목록에 영향이 없다. 반려된 사용자는 같은 룸에 재신청할 수 없다.
    @PostMapping("/v1/rooms/{roomId}/applications/{applicationId}/reject")
    fun reject(
        @PathVariable roomId: UUID,
        @PathVariable applicationId: Long,
        @RequestBody(required = false) request: RejectApplicationRequest?,
    ): ApiResponse<ApplicationDecisionResponse> {
        request?.validate()
        return ApiResponse.success(
            ApplicationDecisionResponse(
                applicationId = applicationId,
                status = "REJECTED",
                statusLabel = "반려",
                recruit = RoomRecruitSummaryResponse(
                    current = 1,
                    max = 6,
                    recruitStatus = "RECRUITING",
                    recruitStatusLabel = "모집 중",
                ),
            ),
        )
    }

    private fun mockApplications(): RoomApplicationsResponse {
        return RoomApplicationsResponse(
            applications = listOf(
                RoomApplicationResponse(
                    applicationId = 3001L,
                    applicant = ApplicantResponse(
                        memberId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        nickname = "성실한 다람쥐 12",
                        jobTitle = "프론트엔드 개발",
                        activitySummary = "완료한 모의면접 8회",
                    ),
                    note = "결제 도메인 1차 면접을 앞두고 있어요. 실전처럼 연습하고 싶어 신청합니다.",
                    aiSummary = ApplicationAiSummaryResponse(
                        status = "DONE",
                        text = "프론트엔드 2년 차. React·TypeScript, 결제 위젯 연동 경험.",
                    ),
                    status = "PENDING",
                    statusLabel = "대기",
                    appliedAt = LocalDateTime.of(2026, 7, 31, 10, 20, 0),
                ),
                RoomApplicationResponse(
                    applicationId = 3002L,
                    applicant = ApplicantResponse(
                        memberId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        nickname = "꼼꼼한 고슴도치 03",
                        jobTitle = "프론트엔드 개발",
                        activitySummary = null,
                    ),
                    note = null,
                    aiSummary = ApplicationAiSummaryResponse(
                        status = "PROCESSING",
                        text = null,
                    ),
                    status = "PENDING",
                    statusLabel = "대기",
                    appliedAt = LocalDateTime.of(2026, 7, 31, 11, 5, 0),
                ),
            ),
        )
    }
}
