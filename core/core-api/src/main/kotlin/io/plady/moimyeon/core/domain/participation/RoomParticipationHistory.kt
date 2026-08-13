package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.trust.RoomReviewStatus

data class RoomParticipationHistory(
    val participatingRooms: List<ParticipatingRoom>,
    val completedRooms: List<CompletedRoomParticipation>,
)

data class ParticipatingRoom(
    val room: Room,
    val participantCount: Int,
)

data class CompletedRoomParticipation(
    val room: Room,
    val attendedParticipantCount: Int,
    val reviewStatus: RoomReviewStatus,
)
