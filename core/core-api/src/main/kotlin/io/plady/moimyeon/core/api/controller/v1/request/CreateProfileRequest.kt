package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.enums.MeetingPreference

data class CreateProfileRequest(
    val nickname: String,
    val jobTitle: String? = null,
    val bio: String? = null,
    val meetingPreference: MeetingPreference? = null,
    val region: String? = null,
)
