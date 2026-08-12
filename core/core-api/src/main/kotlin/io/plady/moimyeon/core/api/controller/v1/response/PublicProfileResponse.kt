package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.JobRole
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.profile.MemberProfile
import io.plady.moimyeon.core.domain.trust.PublicTrust
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.MeetingPreference
import java.util.UUID

data class PublicProfileResponse(
    val memberId: UUID,
    val nickname: String,
    val interestJobRoles: List<PublicProfileJobRoleResponse>,
    val bio: String,
    val meetingPreference: MeetingPreference,
    val trust: PublicProfileTrustResponse,
) {
    companion object {
        fun of(
            member: Member,
            profile: MemberProfile,
            interestJobRoles: List<JobRole>,
            trust: PublicTrust,
        ): PublicProfileResponse {
            val jobRolesById = interestJobRoles.associateBy { it.id }
            return PublicProfileResponse(
                memberId = member.id,
                nickname = member.nickname.value,
                interestJobRoles = profile.interestJobRoleIds.mapNotNull { jobRoleId ->
                    jobRolesById[jobRoleId]?.let(PublicProfileJobRoleResponse::from)
                },
                bio = profile.bio,
                meetingPreference = profile.meetingPreference,
                trust = PublicProfileTrustResponse.from(trust),
            )
        }
    }
}

data class PublicProfileJobRoleResponse(
    val jobRoleId: Long,
    val code: String,
    val displayName: String,
) {
    companion object {
        fun from(jobRole: JobRole): PublicProfileJobRoleResponse {
            return PublicProfileJobRoleResponse(
                jobRoleId = jobRole.id,
                code = jobRole.code,
                displayName = jobRole.displayName,
            )
        }
    }
}

data class PublicProfileTrustResponse(
    val activityTopPercent: Int?,
    val recentAttendances: List<AttendanceStatus>,
    val noShowCount: Int,
    val representativeTags: List<PublicProfileTagResponse>,
) {
    companion object {
        fun from(trust: PublicTrust): PublicProfileTrustResponse {
            return PublicProfileTrustResponse(
                activityTopPercent = trust.activityTopPercent,
                recentAttendances = trust.recentAttendances,
                noShowCount = trust.noShowCount,
                representativeTags = trust.representativeTags.map {
                    PublicProfileTagResponse(label = it.label, count = it.count)
                },
            )
        }
    }
}

data class PublicProfileTagResponse(
    val label: String,
    val count: Int,
)
