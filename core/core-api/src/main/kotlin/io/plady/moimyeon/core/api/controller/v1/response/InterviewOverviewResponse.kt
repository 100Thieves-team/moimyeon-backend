package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.room.RoomSummariesByStatus
import io.plady.moimyeon.core.domain.room.RoomSummary
import io.plady.moimyeon.core.domain.roomapplication.PendingRoomApplication
import io.plady.moimyeon.core.domain.trust.RoomReviewSummary
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
        ): InterviewOverviewResponse {
            val pendingRoomSummariesById = pendingRoomSummaries.associateBy { it.room.id }
            return InterviewOverviewResponse(
                pendingApplications = pendingApplications.mapNotNull { application ->
                    pendingRoomSummariesById[application.roomId]?.let { roomSummary ->
                        PendingRoomApplicationResponse.from(application, roomSummary)
                    }
                },
                participatingRooms = roomSummariesByStatus.active.map(ParticipatingRoomResponse::from),
                completedRooms = roomSummariesByStatus.completed.map { roomSummary ->
                    CompletedRoomResponse.from(roomSummary, reviewSummaries.getValue(roomSummary.room.id))
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
        fun from(application: PendingRoomApplication, roomSummary: RoomSummary): PendingRoomApplicationResponse {
            return PendingRoomApplicationResponse(
                applicationId = application.id,
                resumeOriginalName = application.resumeOriginalName,
                appliedAt = application.appliedAt,
                room = InterviewRoomResponse.from(roomSummary.room, roomSummary.participantCount),
            )
        }
    }
}

data class ParticipatingRoomResponse(
    val room: InterviewRoomResponse,
) {
    companion object {
        fun from(roomSummary: RoomSummary): ParticipatingRoomResponse {
            return ParticipatingRoomResponse(
                InterviewRoomResponse.from(roomSummary.room, roomSummary.participantCount),
            )
        }
    }
}

data class CompletedRoomResponse(
    val room: InterviewRoomResponse,
    val reviewStatus: String,
) {
    companion object {
        fun from(roomSummary: RoomSummary, reviewSummary: RoomReviewSummary): CompletedRoomResponse {
            return CompletedRoomResponse(
                room = InterviewRoomResponse.from(roomSummary.room, reviewSummary.attendedParticipantCount),
                reviewStatus = reviewSummary.status.name,
            )
        }
    }
}

data class InterviewRoomResponse(
    val roomId: UUID,
    val title: String,
    val jobPostingId: Long,
    val jobRoleId: Long,
    val interviewStage: String,
    val interviewType: String?,
    val meetingType: String,
    val sigunguId: Long?,
    val startAt: LocalDateTime,
    val durationMinutes: Int,
    val participantCount: Int,
    val maxParticipants: Int,
    val roomStatus: String,
) {
    companion object {
        fun from(room: Room, participantCount: Int): InterviewRoomResponse {
            val (meetingType, sigunguId) = when (val meetingPlace = room.meetingPlace) {
                MeetingPlace.Online -> "ONLINE" to null
                is MeetingPlace.Offline -> "OFFLINE" to meetingPlace.sigunguId
            }
            return InterviewRoomResponse(
                roomId = room.id,
                title = room.title.value,
                jobPostingId = room.jobPostingId,
                jobRoleId = room.jobRoleId,
                interviewStage = room.interviewStage.name,
                interviewType = room.interviewType?.name,
                meetingType = meetingType,
                sigunguId = sigunguId,
                startAt = room.schedule.startAt,
                durationMinutes = room.schedule.durationMinutes,
                participantCount = participantCount,
                maxParticipants = room.capacity.max,
                roomStatus = room.status.name,
            )
        }
    }
}
