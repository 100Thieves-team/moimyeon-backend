package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.JobRole
import io.plady.moimyeon.core.domain.catalog.RegionLabel
import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.jobposting.JobPostingRef
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.RoomDetail
import io.plady.moimyeon.core.domain.roomviewer.ViewerFacts
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import java.time.LocalDateTime
import java.util.UUID

// 룸 단건 조회 응답 — 룸의 실제 저장 데이터 + 현재 인원 + 방장 식별자 + 표시명 + 조회자 본인의 사실(MOI-500).
// 방장 프로필/신뢰 지표 enrich 는 별도 이슈다.
//
// 판정 결과(가능한 행동·확정 준비 여부)는 내리지 않는다 — 버튼 판정은 화면이 하고,
// 강제는 신청·확정 API 가 실행 시점에 한다. 참조 id 는 표시명 객체 안의 것 하나뿐이다 —
// 공고·직무는 수정 대상이 아니라(RoomUpdateCommand) raw id 를 따로 내릴 자리가 없다.
//
// company·jobPosting·jobRole·region 이 nullable 인 이유는 탐색 목록(RoomSummaryResponse)과 같다 —
// 참조가 끊어져도(회사 미매칭 공고, 폐기된 공고·직무·시군구, 온라인 룸) 룸은 남기고 자리를 비운다.
data class RoomReadResponse(
    val roomId: UUID,
    val status: String, // RoomStatus (RECRUITING | CONFIRMED | IN_PROGRESS | COMPLETED | CANCELED)
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
    val region: RoomRegionResponse?,
    val schedule: RoomReadScheduleResponse,
    val recruit: RoomReadRecruitResponse,
    val resumePublic: Boolean,
    val hostMemberId: UUID,
    // 조회자 본인의 사실(MOI-500). 비로그인이면 null 이다.
    val viewer: RoomViewerResponse?,
) {
    companion object {
        fun from(
            detail: RoomDetail,
            jobPosting: JobPostingRef?,
            company: Company?,
            jobRole: JobRole?,
            region: RegionLabel?,
            viewer: ViewerFacts?,
            now: LocalDateTime,
        ): RoomReadResponse {
            val room = detail.room
            val meetingType = when (room.meetingPlace) {
                MeetingPlace.Online -> MeetingType.ONLINE
                is MeetingPlace.Offline -> MeetingType.OFFLINE
            }
            return RoomReadResponse(
                roomId = room.id,
                status = room.status.name,
                company = company?.let { CompanyResponse(companyId = it.id, name = it.name) },
                jobPosting = jobPosting?.let { RoomJobPostingResponse(it.id, it.postingName) },
                jobRole = jobRole?.let(JobRoleResponse::from),
                title = room.title.value,
                description = room.description?.value,
                round = room.interviewStage.name,
                roundLabel = room.interviewStage.label,
                type = room.interviewType?.name,
                typeLabel = room.interviewType?.label,
                method = meetingType.name,
                methodLabel = meetingType.label,
                region = region?.let { RoomRegionResponse(it.sigunguId, it.label) },
                schedule = RoomReadScheduleResponse(
                    startAt = room.schedule.startAt,
                    durationMinutes = room.schedule.durationMinutes,
                    // 시작 시각과 같아지는 순간부터 지난 것으로 본다 — 신청·확정 검증과 같은 술어다.
                    isPassed = !room.schedule.startAt.isAfter(now),
                ),
                recruit = RoomReadRecruitResponse(
                    current = detail.currentParticipants,
                    min = room.capacity.min,
                    max = room.capacity.max,
                    recruitStatus = detail.recruitStatus.name,
                    recruitStatusLabel = detail.recruitStatus.label,
                    pendingApplicationCount = detail.pendingApplicationCount,
                ),
                resumePublic = room.resumeSharingPolicy == ResumeSharingPolicy.ORIGINAL_AFTER_CONFIRMATION,
                hostMemberId = detail.hostMemberId,
                viewer = viewer?.let(RoomViewerResponse::from),
            )
        }
    }
}

data class RoomReadScheduleResponse(
    val startAt: LocalDateTime,
    val durationMinutes: Int,
    // 서버 시각 기준 일정 경과(MOI-500). 화면이 시계로 재계산하면 신청·확정 API 와 어긋난다.
    val isPassed: Boolean,
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
