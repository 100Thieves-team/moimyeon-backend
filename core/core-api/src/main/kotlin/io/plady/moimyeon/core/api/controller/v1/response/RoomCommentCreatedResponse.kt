package io.plady.moimyeon.core.api.controller.v1.response

import java.time.LocalDateTime

data class RoomCommentCreatedResponse(
    val commentId: Long,
    val createdAt: LocalDateTime,
)
