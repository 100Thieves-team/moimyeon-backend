package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.enums.MeetingPreference
import java.util.UUID

data class MemberProfile(
    val memberId: UUID,
    val jobRoleId: Long?,
    val bio: String?,
    val meetingPreference: MeetingPreference?,
    val sigunguId: Long?,
    // 최초 작성 모달에는 없고 마이페이지 수정에서만 입력됨
    val interestCompanyIds: List<Long> = emptyList(),
)
