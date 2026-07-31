package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.profile.ProfileContent
import io.plady.moimyeon.core.enums.MeetingPreference
import jakarta.validation.constraints.Size

// 직무·지역 참조 id 유효성은 도메인(ProfileService)이 검증
data class CreateProfileRequest(
    val interestJobRoleIds: List<Long> = emptyList(),
    @field:Size(max = 500)
    val bio: String? = null,
    val meetingPreference: MeetingPreference? = null,
    val sigunguId: Long? = null,
) {
    fun toContent(): ProfileContent {
        return ProfileContent(
            bio = bio,
            meetingPreference = meetingPreference,
            sigunguId = sigunguId,
            interestJobRoleIds = interestJobRoleIds,
        )
    }
}
