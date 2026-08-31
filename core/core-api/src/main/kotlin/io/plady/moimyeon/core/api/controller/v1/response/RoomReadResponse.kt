package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.JobRole
import io.plady.moimyeon.core.domain.catalog.RegionLabel
import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.jobposting.JobPostingRef
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.RecruitStatus
import io.plady.moimyeon.core.domain.room.RoomConfirmation
import io.plady.moimyeon.core.domain.room.RoomConfirmationBlockReason
import io.plady.moimyeon.core.domain.room.RoomDetail
import io.plady.moimyeon.core.domain.roomviewer.RoomViewer
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import java.time.LocalDateTime
import java.util.UUID

// 룸 단건 조회 응답 — 룸의 실제 저장 데이터 + 현재 인원 + 방장 식별자 + 표시명(MOI-496).
// 방장 프로필/신뢰 지표 enrich 는 별도 이슈다.
//
// company·jobPosting·jobRole·region 이 nullable 인 이유는 탐색 목록(RoomSummaryResponse)과 같다 —
// 참조가 끊어져도(회사 미매칭 공고, 폐기된 공고·직무·시군구, 온라인 룸) 룸은 남기고 자리를 비운다.
// jobPostingId·jobRoleId·sigunguId 는 표시명 객체와 별개로 유지한다: 수정 폼의 사전 선택값처럼
// 참조가 끊어져도 id 는 필요한 자리가 있다.
data class RoomReadResponse(
    val roomId: UUID,
    val status: String, // RoomStatus (RECRUITING | CONFIRMED | IN_PROGRESS | COMPLETED | CANCELED)
    val jobPostingId: Long,
    val jobRoleId: Long,
    val company: CompanyResponse?,
    val jobPosting: RoomJobPostingResponse?,
    val jobRole: JobRoleResponse?,
    val title: String,
    val description: String?,
    val round: String, // InterviewStage
    val roundLabel: String,
    val type: String?, // InterviewType (선택)
    val typeLabel: String?,
    val method: String, // ONLINE | OFFLINE
    val methodLabel: String,
    val sigunguId: Long?, // OFFLINE 일 때만
    val region: RoomRegionResponse?,
    val schedule: RoomReadScheduleResponse,
    val recruit: RoomReadRecruitResponse,
    val resumePublic: Boolean,
    val hostMemberId: UUID,
    val confirmation: RoomReadConfirmationResponse,
    // "당신이 지금 무엇을 할 수 있나"(MOI-387). 위 confirmation 은 뷰어와 무관한 룸의 사실이라 축이 다르다.
    val viewer: RoomViewerResponse,
) {
    companion object {
        fun from(
            detail: RoomDetail,
            jobPosting: JobPostingRef?,
            company: Company?,
            jobRole: JobRole?,
            region: RegionLabel?,
            confirmation: RoomConfirmation,
            viewer: RoomViewer,
        ): RoomReadResponse {
            val room = detail.room
            val (method, methodLabel, sigunguId) = when (val place = room.meetingPlace) {
                MeetingPlace.Online -> Triple("ONLINE", "온라인", null)
                is MeetingPlace.Offline -> Triple("OFFLINE", "오프라인", place.sigunguId)
            }
            // 모집중/마감은 저장값이 아니라 정원 충족 여부로 계산한다(핵심 결정).
            val recruitStatus = RecruitStatus.of(detail.currentParticipants, room.capacity)
            return RoomReadResponse(
                roomId = room.id,
                status = room.status.name,
                jobPostingId = room.jobPostingId,
                jobRoleId = room.jobRoleId,
                company = company?.let { CompanyResponse(companyId = it.id, name = it.name) },
                jobPosting = jobPosting?.let { RoomJobPostingResponse(it.id, it.postingName) },
                jobRole = jobRole?.let(JobRoleResponse::from),
                title = room.title.value,
                description = room.description?.value,
                round = room.interviewStage.name,
                roundLabel = room.interviewStage.label,
                type = room.interviewType?.name,
                typeLabel = room.interviewType?.label,
                method = method,
                methodLabel = methodLabel,
                sigunguId = sigunguId,
                region = region?.let { RoomRegionResponse(it.sigunguId, it.label) },
                schedule = RoomReadScheduleResponse(
                    startAt = room.schedule.startAt,
                    durationMinutes = room.schedule.durationMinutes,
                ),
                recruit = RoomReadRecruitResponse(
                    current = detail.currentParticipants,
                    min = room.capacity.min,
                    max = room.capacity.max,
                    recruitStatus = recruitStatus.name,
                    recruitStatusLabel = recruitStatus.label,
                    pendingApplicationCount = detail.pendingApplicationCount,
                ),
                resumePublic = room.resumeSharingPolicy == ResumeSharingPolicy.ORIGINAL_AFTER_CONFIRMATION,
                hostMemberId = detail.hostMemberId,
                confirmation = RoomReadConfirmationResponse.from(confirmation, detail),
                viewer = RoomViewerResponse.from(viewer),
            )
        }
    }
}

data class RoomReadScheduleResponse(
    val startAt: LocalDateTime,
    val durationMinutes: Int,
)

data class RoomReadRecruitResponse(
    val current: Int,
    val min: Int,
    val max: Int,
    val recruitStatus: String, // RECRUITING | CLOSED (정원 충족 시 CLOSED)
    val recruitStatusLabel: String, // 모집 중 | 모집 마감
    // 「룸 참여」 §4.1·§6 이 공개로 지정한 값이다. 수만 공개하고 대기자 목록은 방장 외 비공개다.
    val pendingApplicationCount: Int,
)

// "이 룸이 확정될 준비가 됐나"(「진행 확정」 §4.1). 뷰어와 무관한 룸의 사실이며,
// "당신이 확정할 수 있나"는 뷰어 관계가 따로 답한다.
data class RoomReadConfirmationResponse(
    val ready: Boolean,
    val blockReason: RoomReadBlockReasonResponse?,
) {
    companion object {
        fun from(confirmation: RoomConfirmation, detail: RoomDetail): RoomReadConfirmationResponse {
            return RoomReadConfirmationResponse(
                ready = confirmation.ready,
                blockReason = confirmation.blockReason?.let { RoomReadBlockReasonResponse.from(it, detail) },
            )
        }
    }
}

// 라벨은 서버가 소유한다. 인원 미달 문구에는 현재 인원과 최소 인원이 수치로 들어간다.
data class RoomReadBlockReasonResponse(
    val code: String,
    val label: String,
) {
    companion object {
        fun from(reason: RoomConfirmationBlockReason, detail: RoomDetail): RoomReadBlockReasonResponse {
            val label = when (reason) {
                RoomConfirmationBlockReason.ROOM_CONFIRMED -> "이미 확정된 룸이에요"
                RoomConfirmationBlockReason.ROOM_IN_PROGRESS -> "진행 중인 룸이에요"
                RoomConfirmationBlockReason.ROOM_COMPLETED -> "종료된 룸이에요"
                RoomConfirmationBlockReason.ROOM_CANCELED -> "취소된 룸이에요"
                RoomConfirmationBlockReason.SCHEDULE_PASSED -> "진행 일정이 지났어요"
                RoomConfirmationBlockReason.BELOW_MIN_CAPACITY ->
                    "인원 ${detail.currentParticipants} / ${detail.room.capacity.max}명 " +
                        "(최소 ${detail.room.capacity.min}명)"
            }
            return RoomReadBlockReasonResponse(reason.name, label)
        }
    }
}
