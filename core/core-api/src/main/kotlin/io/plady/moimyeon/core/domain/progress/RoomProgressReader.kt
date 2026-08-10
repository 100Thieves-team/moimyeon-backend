package io.plady.moimyeon.core.domain.progress

import java.util.UUID

interface RoomProgressReader {
    fun getAttendance(roomId: UUID, memberId: UUID): Attendance
}
