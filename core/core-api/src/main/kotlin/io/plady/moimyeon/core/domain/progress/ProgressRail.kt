package io.plady.moimyeon.core.domain.progress

import java.util.UUID

class ProgressRail private constructor(
    val blocks: List<ProgressBlock>,
) {
    companion object {
        fun from(confirmedParticipantIds: List<UUID>): ProgressRail {
            return ProgressRail(
                blocks = listOf(ProgressBlock.Opening) +
                    confirmedParticipantIds.map(ProgressBlock::Round) +
                    ProgressBlock.Closing,
            )
        }
    }
}
