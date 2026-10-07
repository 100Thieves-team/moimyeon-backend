package io.plady.moimyeon.core.domain.member

import java.util.UUID

data class MemberAttribution(
    val id: UUID,
    val nickname: String,
    val withdrawn: Boolean,
)
