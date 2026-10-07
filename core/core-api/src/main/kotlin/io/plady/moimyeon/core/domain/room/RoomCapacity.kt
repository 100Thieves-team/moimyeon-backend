package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness

data class RoomCapacity(
    val min: Int,
    val max: Int,
) {
    init {
        requireBusiness(
            min in ALLOWED_RANGE &&
                max in ALLOWED_RANGE &&
                min <= max,
            CoreErrorType.INVALID_ROOM_CAPACITY,
            data = mapOf(
                "min" to min,
                "max" to max,
            ),
        )
    }
    companion object {
        const val MIN_PARTICIPANTS = 2
        const val MAX_PARTICIPANTS = 8
        private val ALLOWED_RANGE = MIN_PARTICIPANTS..MAX_PARTICIPANTS
    }
}
