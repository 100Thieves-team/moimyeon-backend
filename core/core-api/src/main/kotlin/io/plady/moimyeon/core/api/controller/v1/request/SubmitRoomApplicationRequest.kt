package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationForm
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.util.UUID

data class SubmitRoomApplicationRequest(
    val resumeId: UUID,
    val note: String? = null,
) {
    fun toForm(): RoomApplicationForm {
        if (note != null && note.length > NOTE_MAX_LENGTH) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        return RoomApplicationForm(
            resumeId = resumeId,
            note = note.orEmpty(),
        )
    }

    companion object {
        private const val NOTE_MAX_LENGTH = 300
    }
}
