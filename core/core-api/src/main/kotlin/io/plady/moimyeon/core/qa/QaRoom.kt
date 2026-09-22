package io.plady.moimyeon.core.qa

import io.plady.moimyeon.core.enums.RoomStatus
import java.time.LocalDateTime
import java.util.UUID

data class QaRoom(
    val id: UUID,
    val title: String,
    val status: RoomStatus,
    val hostMemberId: UUID?,
    val createdAt: LocalDateTime,
    val applicationCount: Long,
    val participantCount: Long,
)
