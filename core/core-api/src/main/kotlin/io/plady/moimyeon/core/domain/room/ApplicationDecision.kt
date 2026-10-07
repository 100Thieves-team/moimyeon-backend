package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.RoomApplicationStatus

data class ApplicationDecision(
    val applicationId: Long,
    val status: RoomApplicationStatus,
    val currentParticipants: Int,
    val maxCapacity: Int,
)
