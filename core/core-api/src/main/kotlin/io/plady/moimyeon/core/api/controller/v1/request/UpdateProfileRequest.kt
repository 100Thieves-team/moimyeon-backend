package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.profile.ProfileContent
import io.plady.moimyeon.core.enums.MeetingPreference
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    val interestJobRoleIds: List<Long> = emptyList(),
    @field:Size(max = 500)
    val bio: String? = null,
    val interestCompanyIds: List<Long> = emptyList(),
    val meetingPreference: MeetingPreference? = null,
    val sigunguId: Long? = null,
) {
    fun toContent(): ProfileContent {
        return ProfileContent(
            bio = bio,
            meetingPreference = meetingPreference,
            sigunguId = sigunguId,
            interestJobRoleIds = interestJobRoleIds,
            interestCompanyIds = interestCompanyIds,
        )
    }
}
