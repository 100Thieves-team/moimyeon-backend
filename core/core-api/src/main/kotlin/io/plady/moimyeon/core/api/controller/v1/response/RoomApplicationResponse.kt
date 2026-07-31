package io.plady.moimyeon.core.api.controller.v1.response

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import java.util.UUID

// 방장용 참가 신청 목록(GET /v1/rooms/{roomId}/applications) — 「룸 참여」 §4.3.
// 방장만 조회할 수 있고, 전달 사항·AI 요약은 방장 외 비공개다(§6). 이력서 원본으로 가는 경로는 목록에 없다(진행 확정 이후에만).
data class RoomApplicationsResponse(
    val applications: List<RoomApplicationResponse>,
)

data class RoomApplicationResponse(
    val applicationId: Long,
    val applicant: ApplicantResponse,
    val note: String?, // 전달 사항(선택). 방장 외 비공개
    val aiSummary: ApplicationAiSummaryResponse, // 이력서 AI 요약(등록 시점 생성, 재사용)
    val status: String, // PENDING | ACCEPTED | REJECTED | WITHDRAWN
    val statusLabel: String, // 대기 | 수락 | 반려 | 철회
    @get:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val appliedAt: LocalDateTime,
)

// 신청자의 공개 정보(§4.3·§6). 실명·연락처는 노출하지 않는다.
data class ApplicantResponse(
    val memberId: UUID,
    val nickname: String,
    val jobTitle: String?, // 직무
    val activitySummary: String?, // 공개 가능한 활동 정보(완료 룸 수 등, trust 격벽 전까지 자리만)
)

// 이력서 AI 요약. 등록 시점 생성이 아직 안 끝났으면 status=PROCESSING("요약 준비 중")으로 표시한다(J5).
data class ApplicationAiSummaryResponse(
    val status: String, // PROCESSING | DONE | FAILED
    val text: String?,
)

// 수락/반려 결과(POST …/accept · …/reject) — 「룸 참여」 §4.4·§4.9.
// 모집 현황은 결정 반영 후 상태다. 수락은 current 증가·정원 도달 시 CLOSED, 반려는 정원·참여자에 영향 없음.
data class ApplicationDecisionResponse(
    val applicationId: Long,
    val status: String, // ACCEPTED | REJECTED
    val statusLabel: String, // 수락 | 반려
    val recruit: RoomRecruitSummaryResponse,
)
