package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.profile.MemberProfile
import io.plady.moimyeon.core.domain.profile.Nickname
import io.plady.moimyeon.core.enums.MeetingPreference
import jakarta.validation.constraints.Size
import java.util.UUID

// 길이 상한은 스키마 제약의 수송 계층 반영이다. 닉네임 규칙(형식·길이·금칙어)은 도메인 VO(Nickname) 소관.
data class CreateProfileRequest(
    val nickname: String,
    @field:Size(max = 100)
    val jobTitle: String? = null,
    @field:Size(max = 500)
    val bio: String? = null,
    val meetingPreference: MeetingPreference? = null,
    @field:Size(max = 50)
    val region: String? = null,
) {
    fun toProfile(memberId: UUID): MemberProfile {
        return MemberProfile(
            memberId = memberId,
            nickname = Nickname(nickname),
            jobTitle = jobTitle,
            bio = bio,
            meetingPreference = meetingPreference,
            region = region,
        )
    }
}
