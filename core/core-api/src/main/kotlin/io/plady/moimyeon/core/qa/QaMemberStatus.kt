package io.plady.moimyeon.core.qa

import io.plady.moimyeon.core.enums.MemberStatus
import java.util.UUID

data class QaMemberStatus(
    val memberId: UUID,
    val before: MemberStatus,
    val status: MemberStatus,
)
