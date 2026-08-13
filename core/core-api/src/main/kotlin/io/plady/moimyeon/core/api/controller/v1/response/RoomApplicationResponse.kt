package io.plady.moimyeon.core.api.controller.v1.response

import com.fasterxml.jackson.annotation.JsonFormat
import io.plady.moimyeon.core.domain.room.ApplicationDecision
import io.plady.moimyeon.core.enums.RoomApplicationStatus
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
    val note: String, // 전달 사항(미입력 시 빈 문자열). 방장 외 비공개
    val aiSummary: ApplicationAiSummaryResponse,
    val status: String, // PENDING | ACCEPTED | REJECTED | WITHDRAWN | ROOM_CANCELED | ROOM_CONFIRMED | SLOT_EXCEEDED
    val statusLabel: String, // 대기 | 수락 | 반려 | 철회 | 룸 취소 | 진행 확정 | 참여 슬롯 초과
    @get:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val appliedAt: LocalDateTime,
)

// 신청자의 공개 정보(§4.3·§6). 실명·연락처는 노출하지 않는다.
data class ApplicantResponse(
    val memberId: UUID,
    val nickname: String,
    val jobRoles: List<ApplicantJobRoleResponse>,
    val activitySummary: String?, // 공개 가능한 활동 정보(완료 룸 수 등, trust 격벽 전까지 자리만)
)

data class ApplicantJobRoleResponse(
    val jobRoleId: Long,
    val name: String,
)

// 이력서 AI 요약. 등록 시점 생성이 아직 안 끝났으면 status=PROCESSING("요약 준비 중")으로 표시한다.
data class ApplicationAiSummaryResponse(
    val status: String, // PROCESSING | DONE
    val text: String?,
)

// 수락/반려 결과(POST …/accept · …/reject) — 「룸 참여」 §4.4·§4.9.
// 모집 현황은 결정 반영 후 상태다. 수락은 current 증가·정원 도달 시 CLOSED, 반려는 정원·참여자에 영향 없음.
//
// SLOT_EXCEEDED 는 수락 요청의 세 번째 결과다(MOI-427). 신청자의 참여 슬롯이 차 있어 참여자로 등록하지
// 못하고 그 신청만 정리한 경우이며, current 는 늘지 않는다. 실패가 아니라 결정으로 내리는 이유는
// 예외를 던지면 그 정리가 같은 트랜잭션에서 롤백되어 신청이 대기로 남기 때문이다.
data class ApplicationDecisionResponse(
    val applicationId: Long,
    val status: String, // ACCEPTED | REJECTED | SLOT_EXCEEDED
    val statusLabel: String, // 수락 | 반려 | 참여 슬롯 초과
    val recruit: ApplicationRecruitResponse,
) {
    companion object {
        fun from(decision: ApplicationDecision): ApplicationDecisionResponse {
            val closed = decision.currentParticipants >= decision.maxCapacity
            return ApplicationDecisionResponse(
                applicationId = decision.applicationId,
                status = decision.status.name,
                statusLabel = decision.status.label(),
                recruit = ApplicationRecruitResponse(
                    current = decision.currentParticipants,
                    max = decision.maxCapacity,
                    recruitStatus = if (closed) "CLOSED" else "RECRUITING",
                    recruitStatusLabel = if (closed) "모집 마감" else "모집 중",
                ),
            )
        }
    }
}

// 수락/반려 응답의 모집 현황. 필드가 목록 카드와 같아 보이지만 쓰임이 다르다 —
// 목록은 신청 대기 수까지 보여주고(§4.1), 여기는 방금 내린 결정의 결과만 확인시킨다.
// 같은 나열이라고 한 클래스로 묶으면 한쪽이 필드를 늘릴 때 다른 쪽이 끌려간다.
data class ApplicationRecruitResponse(
    val current: Int,
    val max: Int,
    val recruitStatus: String, // RECRUITING | CLOSED (정원 충족 시 CLOSED)
    val recruitStatusLabel: String, // 모집 중 | 모집 마감
)

// 방장이 보는 라벨이다. 신청자에게 보일 문구("룸이 취소됐어요")는 신청자용 응답이 생길 때 정한다.
private fun RoomApplicationStatus.label(): String = when (this) {
    RoomApplicationStatus.PENDING -> "대기"
    RoomApplicationStatus.ACCEPTED -> "수락"
    RoomApplicationStatus.REJECTED -> "반려"
    RoomApplicationStatus.WITHDRAWN -> "철회"
    RoomApplicationStatus.ROOM_CANCELED -> "룸 취소"
    RoomApplicationStatus.ROOM_CONFIRMED -> "진행 확정"
    RoomApplicationStatus.SLOT_EXCEEDED -> "참여 슬롯 초과"
}
