package io.plady.moimyeon.core.qa.controller.response

import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.qa.QaRoomSchedule
import java.time.LocalDateTime
import java.util.UUID

data class QaRoomScheduleResponse(
    val roomId: UUID,
    val status: RoomStatus,
    val startAt: LocalDateTime,
) {
    companion object {
        fun from(schedule: QaRoomSchedule): QaRoomScheduleResponse = QaRoomScheduleResponse(
            roomId = schedule.roomId,
            status = schedule.status,
            startAt = schedule.startAt,
        )
    }
}
