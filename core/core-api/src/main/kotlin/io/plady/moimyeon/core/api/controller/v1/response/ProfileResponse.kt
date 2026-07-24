package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.Company
import io.plady.moimyeon.core.domain.profile.MemberProfile
import io.plady.moimyeon.core.enums.MeetingPreference
import java.util.UUID

data class ProfileResponse(
    val memberId: UUID,
    val nickname: String,
    val jobRoleId: Long?,
    val bio: String?,
    val meetingPreference: MeetingPreference?,
    val sigunguId: Long?,
    val interestCompanies: List<InterestCompanyResponse>,
    val profileCompleted: Boolean,
) {
    companion object {
        fun from(profile: MemberProfile, interestCompanies: List<Company>): ProfileResponse {
            return ProfileResponse(
                memberId = profile.memberId,
                nickname = profile.nickname.value,
                jobRoleId = profile.jobRoleId,
                bio = profile.bio,
                meetingPreference = profile.meetingPreference,
                sigunguId = profile.sigunguId,
                interestCompanies = interestCompanies.map { InterestCompanyResponse(it.id, it.name) },
                profileCompleted = true,
            )
        }
    }
}

data class InterestCompanyResponse(
    val companyId: Long,
    val name: String,
)
