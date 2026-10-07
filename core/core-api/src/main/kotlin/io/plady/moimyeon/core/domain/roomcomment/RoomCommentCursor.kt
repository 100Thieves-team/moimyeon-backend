package io.plady.moimyeon.core.domain.roomcomment

import java.time.LocalDateTime

data class RoomCommentCursor(
    val createdAt: LocalDateTime,
    val id: Long,
)
