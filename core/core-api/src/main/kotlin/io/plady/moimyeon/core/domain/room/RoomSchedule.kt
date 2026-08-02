package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import java.time.LocalDateTime

data class RoomSchedule(
    val startAt: LocalDateTime,
    val durationMinutes: Int,
) {
    init {
        val endAt : LocalDateTime = startAt.plusMinutes(durationMinutes.toLong())
        requireBusiness(durationMinutes > 0, CoreErrorType.INVALID_ROOM_SCHEDULE)
    }
}
