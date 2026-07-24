package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.profile.MemberProfile
import io.plady.moimyeon.core.domain.profile.Nickname
import io.plady.moimyeon.core.enums.MeetingPreference
import jakarta.validation.constraints.Size
import java.util.UUID

data class UpdateProfileRequest(
    val nickname: String,
    val jobRoleId: Long? = null,
    @field:Size(max = 500)
    val bio: String? = null,
    val interestCompanyIds: List<Long> = emptyList(),
    val meetingPreference: MeetingPreference? = null,
    val sigunguId: Long? = null,
) {
    fun toProfile(memberId: UUID): MemberProfile {
        return MemberProfile(
            memberId = memberId,
            nickname = Nickname(nickname),
            jobRoleId = jobRoleId,
            bio = bio,
            meetingPreference = meetingPreference,
            sigunguId = sigunguId,
            interestCompanyIds = interestCompanyIds,
        )
    }
}
