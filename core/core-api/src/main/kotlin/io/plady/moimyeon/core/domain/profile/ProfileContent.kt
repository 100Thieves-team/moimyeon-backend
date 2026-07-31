package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.enums.MeetingPreference

data class ProfileContent(
    val bio: String,
    val meetingPreference: MeetingPreference,
    val sigunguId: Long?,
    val interestJobRoleIds: List<Long>,
    val interestCompanyIds: List<Long>,
)
