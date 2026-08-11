package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import java.util.UUID

// 참여자 명부(GET /v1/rooms/{roomId}/participants) — 「룸 참여」 §4.5.
// 방장·참여자만 조회할 수 있고, 실명·연락처·전달 사항은 실리지 않는다(§6).
// 이력서 원본 URL 도 없다 — 열람은 제출 식별자로 별도 발급한다.
data class RoomParticipantsResponse(
    val participants: List<RoomParticipantResponse>,
)

data class RoomParticipantResponse(
    val memberId: UUID,
    val nickname: String,
    val isHost: Boolean,
    val jobRoles: List<ParticipantJobRoleResponse>,
    val activitySummary: String?, // 완료 룸 수 등 공개 활동 정보. trust 격벽 전까지 자리만
    val aiSummary: ParticipantAiSummaryResponse?,
    val resumeSubmissionId: Long?,
    val canViewOriginal: Boolean,
)

data class ParticipantJobRoleResponse(
    val jobRoleId: Long,
    val name: String,
)

// 요약 실패는 화면에 드러내지 않는다. 신청 목록과 같은 규칙으로 준비 중에 접어 보여준다.
data class ParticipantAiSummaryResponse(
    val status: String, // PROCESSING | DONE
    val text: String?,
) {
    companion object {
        fun from(status: ResumeSummaryStatus, content: String?): ParticipantAiSummaryResponse {
            return when (status) {
                ResumeSummaryStatus.DONE -> ParticipantAiSummaryResponse("DONE", content)
                ResumeSummaryStatus.PROCESSING,
                ResumeSummaryStatus.FAILED,
                -> ParticipantAiSummaryResponse("PROCESSING", null)
            }
        }
    }
}

// 탈퇴로 회원 정보가 사라진 참여자의 표시 문구. 명부는 그래도 성립해야 한다.
const val WITHDRAWN_PARTICIPANT_NICKNAME = "탈퇴한 회원"
