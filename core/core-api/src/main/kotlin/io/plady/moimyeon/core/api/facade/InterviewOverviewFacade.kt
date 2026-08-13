package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.InterviewOverviewResponse
import io.plady.moimyeon.core.domain.participation.RoomParticipantService
import io.plady.moimyeon.core.domain.room.RoomService
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionService
import io.plady.moimyeon.core.domain.trust.TrustService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class InterviewOverviewFacade(
    private val roomApplicationSubmissionService: RoomApplicationSubmissionService,
    private val roomParticipantService: RoomParticipantService,
    private val roomService: RoomService,
    private val trustService: TrustService,
) {
    fun getOverview(memberId: UUID): InterviewOverviewResponse {
        val pendingApplications = roomApplicationSubmissionService.getPendingApplications(memberId)
        val pendingRoomSummaries = roomService.getRoomSummaries(pendingApplications.map { it.roomId })
        val participatingRoomIds = roomParticipantService.getParticipatingRoomIds(memberId)
        val roomSummariesByStatus = roomService.getRoomSummariesByStatus(participatingRoomIds)
        val reviewSummaries = trustService.getRoomReviewSummaries(
            memberId,
            roomSummariesByStatus.completed.map { it.room.id },
        )

        return InterviewOverviewResponse.from(
            pendingApplications = pendingApplications,
            pendingRoomSummaries = pendingRoomSummaries,
            roomSummariesByStatus = roomSummariesByStatus,
            reviewSummaries = reviewSummaries,
        )
    }
}
