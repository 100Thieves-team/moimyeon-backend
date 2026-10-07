package io.plady.moimyeon.core.domain.room

data class RoomSummariesByStatus(
    val active: List<RoomSummary>,
    val completed: List<RoomSummary>,
)
