package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.profile.ProfileContent
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

// 프로필은 가입 시 빈 상태로 만들어져 회원당 항상 하나 존재한다. 이 요청은 그 프로필을 전체 교체한다.
// 아직 작성하지 않은 소개는 null 이 아니라 빈 문자열로 온다.
data class UpdateProfileRequest(
    val interestJobRoleIds: List<Long> = emptyList(),
    val bio: String = "",
    val interestCompanyIds: List<Long> = emptyList(),
) {
    fun toContent(): ProfileContent {
        if (bio.length > BIO_MAX_LENGTH) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)

        return ProfileContent(
            bio = bio,
            interestJobRoleIds = interestJobRoleIds,
            interestCompanyIds = interestCompanyIds,
        )
    }

    companion object {
        private const val BIO_MAX_LENGTH = 500
    }
}
