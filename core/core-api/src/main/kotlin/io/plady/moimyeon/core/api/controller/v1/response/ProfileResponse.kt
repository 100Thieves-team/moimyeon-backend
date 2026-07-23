package io.plady.moimyeon.core.api.controller.v1.response

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
        fun completed(
            memberId: UUID,
            nickname: String,
            jobTitle: String?,
            bio: String?,
            meetingPreference: MeetingPreference?,
            region: String?,
        ): ProfileResponse {
            return ProfileResponse(
                memberId = memberId,
                nickname = nickname,
                jobTitle = jobTitle,
                bio = bio,
                meetingPreference = meetingPreference,
                region = region,
                profileCompleted = true,
            )
        }
    }
}
