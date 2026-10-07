package io.plady.moimyeon.core.domain.progress

import java.util.UUID

sealed interface ProgressBlock {
    data object Opening : ProgressBlock

    data class Round(
        val targetMemberId: UUID,
    ) : ProgressBlock

    data object Closing : ProgressBlock
}
