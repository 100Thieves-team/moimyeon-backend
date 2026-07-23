package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.enums.MemberStatus
import java.util.UUID

data class MemberMeResponse(
    val memberId: UUID,
    val email: String,
    val status: MemberStatus,
    val profileCompleted: Boolean,
    val profile: ProfileResponse?,
)
