package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.roomcomment.RoomCommentCursor
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

data class GetRoomCommentsRequest(
    val cursor: String? = null,
    val size: Int? = null,
) {
    fun toCursor(): RoomCommentCursor? = cursor?.let(RoomCommentCursorToken::decode)

    fun toSize(): Int {
        val resolved = size ?: DEFAULT_SIZE
        if (resolved !in 1..MAX_SIZE) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        return resolved
    }

    companion object {
        private const val DEFAULT_SIZE = 20
        private const val MAX_SIZE = 50
    }
}
