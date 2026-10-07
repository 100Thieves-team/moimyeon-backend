package io.plady.moimyeon.core.api.controller.v1.response

import com.fasterxml.jackson.annotation.JsonFormat
import io.plady.moimyeon.core.domain.room.ApplicationDecision
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import java.time.LocalDateTime
import java.util.UUID

// 이력서 원본으로 가는 경로는 목록에 없다(진행 확정 이후에만).
data class RoomApplicationsResponse(
    val applications: List<RoomApplicationResponse>,
)

data class RoomApplicationResponse(
    val applicationId: Long,
    val applicant: ApplicantResponse,
    val note: String,
    val aiSummary: ApplicationAiSummaryResponse,
    val status: String,
    val statusLabel: String,
    @get:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val appliedAt: LocalDateTime,
)

// 신청자의 공개 정보(§4.3·§6). 실명·연락처는 노출하지 않는다.
data class ApplicantResponse(
    val memberId: UUID,
    val nickname: String,
    val jobRoles: List<ApplicantJobRoleResponse>,
    val activitySummary: String?,
)

data class ApplicantJobRoleResponse(
    val jobRoleId: Long,
    val name: String,
)

data class ApplicationAiSummaryResponse(
    val status: String,
    val text: String?,
)

// SLOT_EXCEEDED 는 수락 요청의 세 번째 결과다(MOI-427). 신청자의 참여 슬롯이 차 있어 참여자로 등록하지
// 못하고 그 신청만 정리한 경우이며, current 는 늘지 않는다. 실패가 아니라 결정으로 내리는 이유는
// 예외를 던지면 그 정리가 같은 트랜잭션에서 롤백되어 신청이 대기로 남기 때문이다.
data class ApplicationDecisionResponse(
    val applicationId: Long,
    val status: String,
    val statusLabel: String,
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
    val recruitStatus: String,
    val recruitStatusLabel: String,
)

// 방장이 보는 라벨이다.
private fun RoomApplicationStatus.label(): String = when (this) {
    RoomApplicationStatus.PENDING -> "대기"
    RoomApplicationStatus.ACCEPTED -> "수락"
    RoomApplicationStatus.REJECTED -> "반려"
    RoomApplicationStatus.WITHDRAWN -> "철회"
    RoomApplicationStatus.ROOM_CANCELED -> "룸 취소"
    RoomApplicationStatus.ROOM_CONFIRMED -> "진행 확정"
    RoomApplicationStatus.SLOT_EXCEEDED -> "참여 슬롯 초과"
}
