package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.profile.ProfileContent
import io.plady.moimyeon.core.enums.MeetingPreference
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

data class UpdateProfileRequest(
    val interestJobRoleIds: List<Long> = emptyList(),
    val bio: String? = null,
    val interestCompanyIds: List<Long> = emptyList(),
    val meetingPreference: MeetingPreference? = null,
    val sigunguId: Long? = null,
) {
    fun toContent(): ProfileContent {
        if (bio != null && bio.length > BIO_MAX_LENGTH) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)

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
