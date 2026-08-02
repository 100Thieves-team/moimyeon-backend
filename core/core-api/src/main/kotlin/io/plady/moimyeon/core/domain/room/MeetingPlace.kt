package io.plady.moimyeon.core.domain.room

sealed interface MeetingPlace {
    data object Online : MeetingPlace
    data class Offline(
        val sigunguId: Long,
    ): MeetingPlace
}