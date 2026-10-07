package io.plady.moimyeon.core.domain.roomapplication

import java.util.UUID

sealed interface ApplicationApplicant {
    val memberId: UUID

    data class Active(
        override val memberId: UUID,
        val nickname: String,
    ) : ApplicationApplicant

    data class Withdrawn(
        override val memberId: UUID,
    ) : ApplicationApplicant
}
