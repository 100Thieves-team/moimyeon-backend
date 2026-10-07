package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.progress.ProgressBlock
import io.plady.moimyeon.core.domain.progress.ProgressRail
import java.util.UUID

data class ProgressRailResponse(
    val blocks: List<ProgressBlockResponse>,
) {
    companion object {
        fun from(rail: ProgressRail, nicknames: Map<UUID, String>): ProgressRailResponse {
            return ProgressRailResponse(
                blocks = rail.blocks.map { block ->
                    when (block) {
                        ProgressBlock.Opening -> ProgressBlockResponse("OPENING", null)
                        is ProgressBlock.Round -> ProgressBlockResponse(
                            type = "ROUND",
                            target = QuestionMemberResponse.of(block.targetMemberId, nicknames),
                        )
                        ProgressBlock.Closing -> ProgressBlockResponse("CLOSING", null)
                    }
                },
            )
        }
    }
}

data class ProgressBlockResponse(
    val type: String,
    val target: QuestionMemberResponse?,
)
