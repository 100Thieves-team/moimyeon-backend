package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.enums.MeetingPreference
import java.util.UUID

data class MemberProfile(
    val memberId: UUID,
    val nickname: Nickname,
    val jobTitle: String?,
    val bio: String?,
    val meetingPreference: MeetingPreference?,
    val region: String?,
)
