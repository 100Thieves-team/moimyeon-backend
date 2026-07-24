package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.profile.MemberProfile
import io.plady.moimyeon.core.enums.MeetingPreference
import java.util.UUID

data class ProfileResponse(
    val memberId: UUID,
    val nickname: String,
    val jobTitle: String?,
    val bio: String?,
    val meetingPreference: MeetingPreference?,
    val region: String?,
    val profileCompleted: Boolean,
) {
    companion object {
        fun from(profile: MemberProfile): ProfileResponse {
            return ProfileResponse(
                memberId = profile.memberId,
                nickname = profile.nickname.value,
                jobTitle = profile.jobTitle,
                bio = profile.bio,
                meetingPreference = profile.meetingPreference,
                region = profile.region,
                profileCompleted = true,
            )
        }
    }
}
