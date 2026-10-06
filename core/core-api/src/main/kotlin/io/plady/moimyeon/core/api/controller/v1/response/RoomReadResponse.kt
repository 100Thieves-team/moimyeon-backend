package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.JobRole
import io.plady.moimyeon.core.domain.catalog.RegionLabel
import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.jobposting.JobPostingRef
import io.plady.moimyeon.core.domain.participation.JoinedParticipant
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.RoomDetail
import io.plady.moimyeon.core.domain.roomviewer.ViewerFacts
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import java.time.LocalDateTime
import java.util.UUID

// 방장 프로필/신뢰 지표 enrich 는 별도 이슈다.
//
// 참조 id 는 표시명 객체 안의 것 하나뿐이다 —
// 공고·직무는 수정 대상이 아니라(RoomUpdateCommand) raw id 를 따로 내릴 자리가 없다.
data class RoomReadResponse(
    val roomId: UUID,
    val status: String,
    val company: CompanyResponse?,
    val jobPosting: RoomJobPostingResponse?,
    val jobRole: JobRoleResponse?,
    val title: String,
    val description: String?,
    val round: String,
    val roundLabel: String,
    val type: String?,
    val typeLabel: String?,
    val method: String,
    val methodLabel: String,
    val region: RoomRegionResponse?,
    val schedule: RoomReadScheduleResponse,
    val recruit: RoomReadRecruitResponse,
    val resumePublic: Boolean,
    val previouslyConfirmed: Boolean,
    val hostMemberId: UUID,
    val participants: List<RoomReadParticipantResponse>,
    val viewer: RoomViewerResponse?,
) {
    companion object {
        fun from(
            detail: RoomDetail,
            jobPosting: JobPostingRef?,
            company: Company?,
            jobRole: JobRole?,
            region: RegionLabel?,
            joinedParticipants: List<JoinedParticipant>,
            nicknames: Map<UUID, String>,
            viewer: ViewerFacts?,
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
                previouslyConfirmed = detail.previouslyConfirmed,
                hostMemberId = detail.hostMemberId,
                participants = joinedParticipants.map {
                    RoomReadParticipantResponse(
                        memberId = it.memberId,
                        nickname = nicknames[it.memberId] ?: WITHDRAWN_PARTICIPANT_NICKNAME,
                    )
                },
                viewer = viewer?.let(RoomViewerResponse::from),
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
    val recruitStatus: String,
    val recruitStatusLabel: String,
    val pendingApplicationCount: Int,
)

data class RoomReadParticipantResponse(
    val memberId: UUID,
    val nickname: String,
)
