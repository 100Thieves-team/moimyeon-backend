package io.plady.moimyeon.core.domain.participation

data class RoomParticipants(
    val participants: List<RoomParticipant>,
    val confirmedParticipants: List<ConfirmedParticipant>,
)
