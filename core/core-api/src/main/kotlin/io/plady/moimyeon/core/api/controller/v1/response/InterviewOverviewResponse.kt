package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.JobRole
import io.plady.moimyeon.core.domain.catalog.RegionLabel
import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.jobposting.JobPostingRef
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.room.RoomSummariesByStatus
import io.plady.moimyeon.core.domain.room.RoomSummary
import io.plady.moimyeon.core.domain.roomapplication.PendingRoomApplication
import io.plady.moimyeon.core.domain.trust.RoomReviewSummary
import io.plady.moimyeon.core.enums.MeetingType
import java.time.LocalDateTime
import java.util.UUID

data class InterviewOverviewResponse(
    val pendingApplications: List<PendingRoomApplicationResponse>,
    val participatingRooms: List<ParticipatingRoomResponse>,
    val completedRooms: List<CompletedRoomResponse>,
) {
    companion object {
        fun from(
            pendingApplications: List<PendingRoomApplication>,
            pendingRoomSummaries: Collection<RoomSummary>,
            roomSummariesByStatus: RoomSummariesByStatus,
            reviewSummaries: Map<UUID, RoomReviewSummary>,
            refs: InterviewRoomRefs,
        ): InterviewOverviewResponse {
            val pendingRoomSummariesById = pendingRoomSummaries.associateBy { it.room.id }
            return InterviewOverviewResponse(
                pendingApplications = pendingApplications.mapNotNull { application ->
                    pendingRoomSummariesById[application.roomId]?.let { roomSummary ->
                        PendingRoomApplicationResponse.from(application, roomSummary, refs)
                    }
                },
                participatingRooms = roomSummariesByStatus.active.map { ParticipatingRoomResponse.from(it, refs) },
                completedRooms = roomSummariesByStatus.completed.map { roomSummary ->
                    CompletedRoomResponse.from(roomSummary, reviewSummaries.getValue(roomSummary.room.id), refs)
                },
            )
        }
    }
}

data class PendingRoomApplicationResponse(
    val applicationId: Long,
    val resumeOriginalName: String,
    val appliedAt: LocalDateTime,
    val room: InterviewRoomResponse,
) {
    companion object {
        fun from(
            application: PendingRoomApplication,
            roomSummary: RoomSummary,
            refs: InterviewRoomRefs,
        ): PendingRoomApplicationResponse {
            return PendingRoomApplicationResponse(
                applicationId = application.id,
                resumeOriginalName = application.resumeOriginalName,
                appliedAt = application.appliedAt,
                room = InterviewRoomResponse.from(roomSummary.room, roomSummary.participantCount, refs),
            )
        }
    }
}

data class ParticipatingRoomResponse(
    val room: InterviewRoomResponse,
) {
    companion object {
        fun from(roomSummary: RoomSummary, refs: InterviewRoomRefs): ParticipatingRoomResponse {
            return ParticipatingRoomResponse(
                InterviewRoomResponse.from(roomSummary.room, roomSummary.participantCount, refs),
            )
        }
    }
}

data class CompletedRoomResponse(
    val room: InterviewRoomResponse,
    val reviewStatus: String,
) {
    companion object {
        fun from(
            roomSummary: RoomSummary,
            reviewSummary: RoomReviewSummary,
            refs: InterviewRoomRefs,
        ): CompletedRoomResponse {
            return CompletedRoomResponse(
                room = InterviewRoomResponse.from(roomSummary.room, reviewSummary.attendedParticipantCount, refs),
                reviewStatus = reviewSummary.status.name,
            )
        }
    }
}

// 표시명 참조 묶음. 조회는 Facade 가 세 구분의 룸을 합쳐 한 번에 끝내고, 여기서는 id 로 찾아 붙이기만 한다.
// company·jobPosting·jobRole·region 이 nullable 인 이유는 탐색 목록(RoomSummaryResponse)과 같다 —
// 참조가 끊어져도 룸은 남기고 자리를 비운다.
data class InterviewRoomRefs(
    val jobPostings: Map<Long, JobPostingRef>,
    val companies: Map<Long, Company>,
    val jobRoles: Map<Long, JobRole>,
    val regions: Map<Long, RegionLabel>,
) {
    companion object {
        val EMPTY = InterviewRoomRefs(emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }
}

data class InterviewRoomResponse(
    val roomId: UUID,
    val title: String,
    val jobPostingId: Long,
    val jobRoleId: Long,
    val company: CompanyResponse?,
    val jobPosting: RoomJobPostingResponse?,
    val jobRole: JobRoleResponse?,
    val interviewStage: String,
    val interviewStageLabel: String,
    val interviewType: String?,
    val interviewTypeLabel: String?,
    val meetingType: String,
    val meetingTypeLabel: String,
    val sigunguId: Long?,
    val region: RoomRegionResponse?,
    val startAt: LocalDateTime,
    val durationMinutes: Int,
    val participantCount: Int,
    val maxParticipants: Int,
    val roomStatus: String,
) {
    companion object {
        fun from(room: Room, participantCount: Int, refs: InterviewRoomRefs): InterviewRoomResponse {
            val (meetingType, sigunguId) = when (val meetingPlace = room.meetingPlace) {
                MeetingPlace.Online -> MeetingType.ONLINE to null
                is MeetingPlace.Offline -> MeetingType.OFFLINE to meetingPlace.sigunguId
            }
            val jobPosting = refs.jobPostings[room.jobPostingId]
            return InterviewRoomResponse(
                roomId = room.id,
                title = room.title.value,
                jobPostingId = room.jobPostingId,
                jobRoleId = room.jobRoleId,
                company = jobPosting?.companyId?.let { refs.companies[it] }
                    ?.let { CompanyResponse(companyId = it.id, name = it.name) },
                jobPosting = jobPosting?.let { RoomJobPostingResponse(it.id, it.postingName) },
                jobRole = refs.jobRoles[room.jobRoleId]?.let(JobRoleResponse::from),
                interviewStage = room.interviewStage.name,
                interviewStageLabel = room.interviewStage.label,
                interviewType = room.interviewType?.name,
                interviewTypeLabel = room.interviewType?.label,
                meetingType = meetingType.name,
                meetingTypeLabel = meetingType.label,
                sigunguId = sigunguId,
                region = sigunguId?.let { refs.regions[it] }
                    ?.let { RoomRegionResponse(it.sigunguId, it.label) },
                startAt = room.schedule.startAt,
                durationMinutes = room.schedule.durationMinutes,
                participantCount = participantCount,
                maxParticipants = room.capacity.max,
                roomStatus = room.status.name,
            )
        }
    }
}
