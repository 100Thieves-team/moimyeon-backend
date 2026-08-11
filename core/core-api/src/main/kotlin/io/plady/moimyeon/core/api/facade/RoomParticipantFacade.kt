package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.ParticipantAiSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.ParticipantJobRoleResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomParticipantResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomParticipantsResponse
import io.plady.moimyeon.core.api.controller.v1.response.WITHDRAWN_PARTICIPANT_NICKNAME
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.catalog.JobRole
import io.plady.moimyeon.core.domain.participation.RoomParticipant
import io.plady.moimyeon.core.domain.participation.RoomParticipantService
import io.plady.moimyeon.core.domain.profile.ProfileService
import org.springframework.stereotype.Component
import java.util.UUID

// 명부의 판정(권한·원본 열람 가능 여부)은 도메인이 끝내고, 여기서는 표시에 필요한 직무 이름만 붙인다.
@Component
class RoomParticipantFacade(
    private val roomParticipantService: RoomParticipantService,
    private val profileService: ProfileService,
    private val catalogService: CatalogService,
) {
    fun getParticipants(viewerMemberId: UUID, roomId: UUID): RoomParticipantsResponse {
        val participants = roomParticipantService.getParticipants(viewerMemberId, roomId)
        if (participants.isEmpty()) return RoomParticipantsResponse(emptyList())

        // 프로필·카탈로그도 일괄로 가져온다. 참여자 수에 비례해 쿼리가 늘면 안 된다.
        val jobRoleIdsByMemberId = profileService.getProfiles(participants.map { it.memberId })
            .associate { it.memberId to it.interestJobRoleIds.toSet() }
        val interestedJobRoleIds = jobRoleIdsByMemberId.values.flatten().toSet()
        val jobRoles = if (interestedJobRoleIds.isEmpty()) {
            emptyList()
        } else {
            catalogService.getJobCatalog()
                .flatMap { it.roles }
                .filter { it.id in interestedJobRoleIds }
        }

        return RoomParticipantsResponse(
            participants = participants.map { it.toResponse(jobRoleIdsByMemberId, jobRoles) },
        )
    }

    private fun RoomParticipant.toResponse(
        jobRoleIdsByMemberId: Map<UUID, Set<Long>>,
        jobRoles: List<JobRole>,
    ): RoomParticipantResponse {
        val interestedJobRoleIds = jobRoleIdsByMemberId[memberId].orEmpty()
        return RoomParticipantResponse(
            memberId = memberId,
            nickname = nickname ?: WITHDRAWN_PARTICIPANT_NICKNAME,
            isHost = isHost,
            jobRoles = jobRoles
                .filter { it.id in interestedJobRoleIds }
                .map { ParticipantJobRoleResponse(it.id, it.displayName) },
            activitySummary = null,
            aiSummary = resumeSummary?.let { ParticipantAiSummaryResponse.from(it.status, it.content) },
            resumeSubmissionId = resumeSubmissionId,
            canViewOriginal = canViewOriginal,
        )
    }
}
