package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.profile.ProfileContent
import io.plady.moimyeon.core.enums.MeetingPreference
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    val jobRoleId: Long? = null,
    @field:Size(max = 500)
    val bio: String? = null,
    val interestCompanyIds: List<Long> = emptyList(),
    val meetingPreference: MeetingPreference? = null,
    val sigunguId: Long? = null,
) {
    fun toContent(): ProfileContent {
        return ProfileContent(
            jobRoleId = jobRoleId,
            bio = bio,
            meetingPreference = meetingPreference,
            sigunguId = sigunguId,
            interestCompanyIds = interestCompanyIds,
        )
    }
}
