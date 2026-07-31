package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.enums.MeetingPreference

// 프로필 작성/수정의 입력 내용. 식별자는 담지 않는다 — memberId 는 인증 주체에서 온다.
data class ProfileContent(
    val bio: String?,
    val meetingPreference: MeetingPreference?,
    val sigunguId: Long?,
    val interestJobRoleIds: List<Long> = emptyList(),
    val interestCompanyIds: List<Long> = emptyList(),
)
