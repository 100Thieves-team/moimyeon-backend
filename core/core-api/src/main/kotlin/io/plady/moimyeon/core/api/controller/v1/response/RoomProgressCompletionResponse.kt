package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.progress.RoomProgressCompletionResult
data class RoomProgressCompletionResponse(
    val status: String,
) {
    companion object {
        fun from(result: RoomProgressCompletionResult): RoomProgressCompletionResponse = RoomProgressCompletionResponse(
            status = result.status.name,
        )
    }
}
