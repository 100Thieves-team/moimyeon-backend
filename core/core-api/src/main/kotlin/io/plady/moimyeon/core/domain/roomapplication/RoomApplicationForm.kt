package io.plady.moimyeon.core.domain.roomapplication

import java.util.UUID

data class RoomApplicationForm(
    val resumeId: UUID,
    val note: String,
)
