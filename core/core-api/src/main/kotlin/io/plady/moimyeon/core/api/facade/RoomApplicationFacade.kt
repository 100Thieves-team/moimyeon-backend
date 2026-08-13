package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.ApplicantJobRoleResponse
import io.plady.moimyeon.core.api.controller.v1.response.ApplicantResponse
import io.plady.moimyeon.core.api.controller.v1.response.ApplicationAiSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationsResponse
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.catalog.JobRole
import io.plady.moimyeon.core.domain.profile.ProfileService
import io.plady.moimyeon.core.domain.roomapplication.ApplicationApplicant
import io.plady.moimyeon.core.domain.roomapplication.ApplicationResumeSummary
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationDetails
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionService
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomApplicationFacade(
    private val roomApplicationSubmissionService: RoomApplicationSubmissionService,
    private val profileService: ProfileService,
    private val catalogService: CatalogService,
) {
    fun getApplications(requesterMemberId: UUID, roomId: UUID): RoomApplicationsResponse {
        val applications = roomApplicationSubmissionService.getApplications(requesterMemberId, roomId)
        val activeMemberIds = applications
            .mapNotNull { (it.applicant as? ApplicationApplicant.Active)?.memberId }
            .distinct()
        val profiles = if (activeMemberIds.isEmpty()) emptyList() else profileService.getProfiles(activeMemberIds)
        val jobRoleIdsByMemberId = profiles
            .associate { it.memberId to it.interestJobRoleIds.toSet() }
        val interestedJobRoleIds = jobRoleIdsByMemberId.values.flatten().toSet()
        val jobRoles = if (interestedJobRoleIds.isEmpty()) {
            emptyList()
        } else {
            catalogService.getJobCatalog()
                .flatMap { it.roles }
                .filter { it.id in interestedJobRoleIds }
        }

        return RoomApplicationsResponse(
            applications = applications.map { it.toResponse(jobRoleIdsByMemberId, jobRoles) },
        )
    }

    private fun RoomApplicationDetails.toResponse(
        jobRoleIdsByMemberId: Map<UUID, Set<Long>>,
        jobRoles: List<JobRole>,
    ): RoomApplicationResponse {
        return RoomApplicationResponse(
            applicationId = applicationId,
            applicant = applicant.toResponse(jobRoleIdsByMemberId, jobRoles),
            note = note,
            aiSummary = resumeSummary.toResponse(),
            status = status.name,
            statusLabel = status.label(),
            appliedAt = appliedAt,
        )
    }

    private fun ApplicationApplicant.toResponse(
        jobRoleIdsByMemberId: Map<UUID, Set<Long>>,
        jobRoles: List<JobRole>,
    ): ApplicantResponse {
        return when (this) {
            is ApplicationApplicant.Active -> {
                val interestedJobRoleIds = jobRoleIdsByMemberId.getValue(memberId)
                ApplicantResponse(
                    memberId = memberId,
                    nickname = nickname,
                    jobRoles = jobRoles
                        .filter { it.id in interestedJobRoleIds }
                        .map { ApplicantJobRoleResponse(it.id, it.displayName) },
                    activitySummary = null,
                )
            }

            is ApplicationApplicant.Withdrawn -> ApplicantResponse(
                memberId = memberId,
                nickname = WITHDRAWN_MEMBER_NICKNAME,
                jobRoles = emptyList(),
                activitySummary = null,
            )
        }
    }

    private fun ApplicationResumeSummary.toResponse(): ApplicationAiSummaryResponse {
        return when (this) {
            is ApplicationResumeSummary.Ready -> ApplicationAiSummaryResponse("DONE", content)
            ApplicationResumeSummary.Preparing -> ApplicationAiSummaryResponse("PROCESSING", null)
        }
    }

    // 방장이 보는 라벨이다. 신청자에게 보일 문구("룸이 취소됐어요")는 신청자용 응답이 생길 때 정한다.
    private fun RoomApplicationStatus.label(): String = when (this) {
        RoomApplicationStatus.PENDING -> "대기"
        RoomApplicationStatus.ACCEPTED -> "수락"
        RoomApplicationStatus.REJECTED -> "반려"
        RoomApplicationStatus.WITHDRAWN -> "철회"
        RoomApplicationStatus.ROOM_CANCELED -> "룸 취소"
        RoomApplicationStatus.ROOM_CONFIRMED -> "진행 확정"
        RoomApplicationStatus.SLOT_EXCEEDED -> "참여 슬롯 초과"
    }
}

private const val WITHDRAWN_MEMBER_NICKNAME = "탈퇴한 사용자"
