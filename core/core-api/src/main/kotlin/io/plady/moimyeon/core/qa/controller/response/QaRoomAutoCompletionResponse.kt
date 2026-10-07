package io.plady.moimyeon.core.qa.controller.response

import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.qa.QaRoomAutoCompletion
import java.util.UUID

data class QaRoomAutoCompletionResponse(
    val roomId: UUID,
    val completed: Boolean,
    val status: RoomStatus,
) {
    companion object {
        fun from(result: QaRoomAutoCompletion): QaRoomAutoCompletionResponse = QaRoomAutoCompletionResponse(
            roomId = result.roomId,
            completed = result.completed,
            status = result.status,
        )
    }
}
