package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.JobRole
import io.plady.moimyeon.core.domain.catalog.RegionLabel
import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.jobposting.JobPostingRef
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.RoomCard
import io.plady.moimyeon.core.domain.roomviewer.ViewerFacts
import io.plady.moimyeon.core.enums.MeetingType
import java.util.UUID

data class RoomsResponse(
    val rooms: List<RoomSummaryResponse>,
    val sort: String,
    val totalCount: Int,
    val nextCursor: String?,
)

// 상세보다 가벼운 공개 정보만 담는다(오프라인 상세 주소·이력서 등 민감 정보 제외, §4.4).
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
    val viewer: RoomViewerResponse?,
) {
    companion object {
        fun from(
            card: RoomCard,
            jobPosting: JobPostingRef?,
            company: Company?,
            jobRole: JobRole?,
            region: RegionLabel?,
            viewer: ViewerFacts?,
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
                method = room.meetingPlace.meetingType().name,
                methodLabel = room.meetingPlace.meetingType().label,
                region = region?.let { RoomRegionResponse(it.sigunguId, it.label) },
                schedule = RoomScheduleResponse.from(room.schedule),
                recruit = RoomRecruitSummaryResponse.from(card),
                viewer = viewer?.let(RoomViewerResponse::from),
            )
        }

        // 라벨의 단일 소스는 enum 이다(폼 선택지 계약과 같은 정의).
        private fun MeetingPlace.meetingType(): MeetingType = when (this) {
            MeetingPlace.Online -> MeetingType.ONLINE
            is MeetingPlace.Offline -> MeetingType.OFFLINE
        }
    }
}

// 모집 중/마감은 저장값이 아니라 정원 충족 여부로 계산된 값이다.
data class RoomRecruitSummaryResponse(
    val current: Int,
    val max: Int,
    val pending: Int,
    val recruitStatus: String,
    val recruitStatusLabel: String,
) {
    companion object {
        fun from(card: RoomCard): RoomRecruitSummaryResponse = RoomRecruitSummaryResponse(
            current = card.currentParticipants,
            max = card.room.capacity.max,
            pending = card.pendingApplications,
            recruitStatus = card.recruitStatus.name,
            recruitStatusLabel = card.recruitStatus.label,
        )
    }
}
