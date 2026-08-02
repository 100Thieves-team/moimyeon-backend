package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness

@JvmInline
value class RoomDescription(
    val value: String,
) {
    init {
        requireBusiness(
            value.length <= MAX_LENGTH,
            CoreErrorType.INVALID_ROOM_DESCRIPTION,

        )
    }
    companion object {
        private const val MAX_LENGTH = 1_000
    }
}
