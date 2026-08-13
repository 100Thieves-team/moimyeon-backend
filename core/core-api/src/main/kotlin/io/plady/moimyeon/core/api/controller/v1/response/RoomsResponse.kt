package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.JobRole
import io.plady.moimyeon.core.domain.catalog.RegionLabel
import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.jobposting.JobPostingRef
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.RecruitStatus
import io.plady.moimyeon.core.domain.room.RoomCard
import io.plady.moimyeon.core.domain.roomviewer.RoomViewer
import java.util.UUID

// 룸 탐색 목록(GET /v1/rooms) — 「룸 탐색」 §4.1·§4.3. 완료·취소·일정 경과 룸은 제외된다.
// 서로 다른 필터는 AND 로 결합하고, 지원하지 않는 정렬 값은 기본 정렬로 처리한 뒤 그 결과를 sort 에 담는다.
data class RoomsResponse(
    val rooms: List<RoomSummaryResponse>,
    val sort: String, // SCHEDULE | RECENT (실제로 적용된 정렬)
    val totalCount: Int,
    // 커서 페이지네이션. null 이면 마지막 페이지다. 내용은 해석하지 말고 그대로 다시 보낸다.
    val nextCursor: String?,
)

// 목록 카드 한 건. 상세보다 가벼운 공개 정보만 담는다(오프라인 상세 주소·이력서 등 민감 정보 제외, §4.4).
//
// company·jobPosting·jobRole·region 이 모두 nullable 인 이유: 이 값들은 룸이 참조하는 다른 개념에서
// 파생되는데, 그 참조가 끊어질 수 있다(회사 미매칭 공고, 폐기된 공고·직무·시군구, 온라인 룸).
// 그때 룸을 목록에서 빼면 방장은 자기 룸이 왜 안 보이는지 알 수 없으므로, 룸은 남기고 자리를 비운다.
data class RoomSummaryResponse(
    val roomId: UUID,
    val title: String,
    val company: CompanyResponse?,
    val jobPosting: RoomJobPostingResponse?,
    val jobRole: JobRoleResponse?,
    val round: String,
    val roundLabel: String,
    val type: String?,
    val typeLabel: String?,
    val method: String,
    val methodLabel: String,
    val region: RoomRegionResponse?,
    val schedule: RoomScheduleResponse,
    val recruit: RoomRecruitSummaryResponse,
    // 보는 사람에 따라 다음 행동이 갈린다(MOI-387). 룸의 공개 정보 자체는 뷰어와 무관하다.
    val viewer: RoomViewerResponse,
) {
    companion object {
        fun from(
            card: RoomCard,
            jobPosting: JobPostingRef?,
            company: Company?,
            jobRole: JobRole?,
            region: RegionLabel?,
            viewer: RoomViewer,
        ): RoomSummaryResponse {
            val room = card.room
            return RoomSummaryResponse(
                roomId = room.id,
                title = room.title.value,
                company = company?.let { CompanyResponse(companyId = it.id, name = it.name) },
                jobPosting = jobPosting?.let { RoomJobPostingResponse(it.id, it.postingName) },
                jobRole = jobRole?.let(JobRoleResponse::from),
                round = room.interviewStage.name,
                roundLabel = room.interviewStage.label,
                type = room.interviewType?.name,
                typeLabel = room.interviewType?.label,
                method = room.meetingPlace.methodCode(),
                methodLabel = room.meetingPlace.methodLabel(),
                region = region?.let { RoomRegionResponse(it.sigunguId, it.label) },
                schedule = RoomScheduleResponse.from(room.schedule),
                recruit = RoomRecruitSummaryResponse.from(card),
                viewer = RoomViewerResponse.from(viewer),
            )
        }

        private fun MeetingPlace.methodCode(): String = when (this) {
            MeetingPlace.Online -> "ONLINE"
            is MeetingPlace.Offline -> "OFFLINE"
        }

        private fun MeetingPlace.methodLabel(): String = when (this) {
            MeetingPlace.Online -> "온라인"
            is MeetingPlace.Offline -> "오프라인"
        }
    }
}

// 목록·수락/반려 응답이 공유하는 경량 모집 현황. 모집 중/마감은 저장값이 아니라 정원 충족 여부로 계산된 값이다.
data class RoomRecruitSummaryResponse(
    val current: Int,
    val max: Int,
    // 대기 중인 참가 신청 수(2026-08-04 PRD 갱신). 비로그인에도 공개되는 값이다.
    val pending: Int,
    val recruitStatus: String, // RECRUITING | CLOSED (정원 충족 시 CLOSED)
    val recruitStatusLabel: String, // 모집 중 | 모집 마감
) {
    companion object {
        fun from(card: RoomCard): RoomRecruitSummaryResponse = RoomRecruitSummaryResponse(
            current = card.currentParticipants,
            max = card.room.capacity.max,
            pending = card.pendingApplications,
            recruitStatus = card.recruitStatus.name,
            recruitStatusLabel = when (card.recruitStatus) {
                RecruitStatus.RECRUITING -> "모집 중"
                RecruitStatus.CLOSED -> "모집 마감"
            },
        )
    }
}
