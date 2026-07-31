package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.profile.ProfileContent
import io.plady.moimyeon.core.enums.MeetingPreference
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

// 프로필은 가입 시 빈 상태로 만들어져 회원당 항상 하나 존재한다. 이 요청은 그 프로필을 전체 교체한다.
// 미지정은 null 이 아니라 값으로 온다 — 소개는 빈 문자열, 만남 선호는 UNSPECIFIED.
// 지역만 카탈로그 참조 id 라 "안 고름"을 null 로 표현한다.
data class UpdateProfileRequest(
    val interestJobRoleIds: List<Long> = emptyList(),
    val bio: String = "",
    val interestCompanyIds: List<Long> = emptyList(),
    val meetingPreference: MeetingPreference = MeetingPreference.UNSPECIFIED,
    val sigunguId: Long? = null,
) {
    fun toContent(): ProfileContent {
        if (bio.length > BIO_MAX_LENGTH) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)

        return ProfileContent(
            bio = bio,
            meetingPreference = meetingPreference,
            sigunguId = sigunguId,
            interestJobRoleIds = interestJobRoleIds,
            interestCompanyIds = interestCompanyIds,
        )
    }

    companion object {
        private const val BIO_MAX_LENGTH = 500
    }
}
