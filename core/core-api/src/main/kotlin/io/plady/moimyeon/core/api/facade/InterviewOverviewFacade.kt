package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.InterviewOverviewResponse
import io.plady.moimyeon.core.api.controller.v1.response.InterviewRoomRefs
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import io.plady.moimyeon.core.domain.participation.RoomParticipantService
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.Room
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
    private val jobPostingService: JobPostingService,
    private val companyService: CompanyService,
    private val catalogService: CatalogService,
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
            refs = resolveRefs(
                pendingRoomSummaries.map { it.room } +
                    roomSummariesByStatus.active.map { it.room } +
                    roomSummariesByStatus.completed.map { it.room },
            ),
        )
    }

    // 표시명 조회는 탐색 목록(RoomSearchFacade)과 같은 규칙이다: 회사는 룸이 직접 참조하지 않아
    // room → job_posting → company 2단계로 읽고, 세 구분의 룸을 합쳐 구분당이 아니라 응답당 상수 개수로 조회한다.
    private fun resolveRefs(rooms: List<Room>): InterviewRoomRefs {
        val jobPostings = jobPostingService.getRefs(rooms.map { it.jobPostingId }.toSet())
            .associateBy { it.id }
        return InterviewRoomRefs(
            jobPostings = jobPostings,
            companies = companyService.getCompanies(jobPostings.values.mapNotNull { it.companyId }.toSet())
                .associateBy { it.id },
            jobRoles = catalogService.getJobRoles(rooms.map { it.jobRoleId }.toSet())
                .associateBy { it.id },
            regions = catalogService.getRegionLabels(
                rooms.mapNotNull { (it.meetingPlace as? MeetingPlace.Offline)?.sigunguId }.toSet(),
            ).associateBy { it.sigunguId },
        )
    }
}
