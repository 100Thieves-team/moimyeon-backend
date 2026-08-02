package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.storage.db.core.RoomEntity

object RoomMapper {
    fun toDomain(entity: RoomEntity): Room =
        Room.reconstitute(
            id = entity.id,
            jobPostingId = entity.jobPostingId,
            jobRoleId = entity.jobRoleId,
            title = RoomTitle(entity.title),
            description = entity.description?.let(::RoomDescription),
            interviewStage = entity.interviewStage,
            interviewType = entity.interviewType,
            meetingPlace = entity.toMeetingPlace(),
            capacity = RoomCapacity(
                min = entity.minCapacity.toInt(),
                max = entity.maxCapacity.toInt(),
            ),
            schedule = RoomSchedule(
                startAt = entity.startAt,
                durationMinutes = entity.durationMinutes.toInt(),
            ),
            resumeSharingPolicy =
                entity.resumePublic.toResumeSharingPolicy(),
            status = entity.status,
        )


    fun toEntity(room: Room): RoomEntity {
        val (meetingType, sigunguId) = room.meetingPlace.toEntityValues()
        return RoomEntity(
            id = room.id,
            jobPostingId = room.jobPostingId,
            jobRoleId = room.jobRoleId,
            sigunguId = sigunguId,
            title = room.title.value,
            description = room.description?.value,
            interviewStage = room.interviewStage,
            interviewType = room.interviewType,
            meetingType = meetingType,
            minCapacity = room.capacity.min.toShort(),
            maxCapacity = room.capacity.max.toShort(),
            startAt = room.schedule.startAt,
            durationMinutes = room.schedule.durationMinutes.toShort(),
            resumePublic = room.resumeSharingPolicy.toResumePublic(),
        )
    }

    private fun ResumeSharingPolicy.toResumePublic(): Boolean =
        when (this) {
            ResumeSharingPolicy.AI_SUMMARY_ONLY -> false
            ResumeSharingPolicy.ORIGINAL_AFTER_CONFIRMATION -> true
        }

    private fun Boolean.toResumeSharingPolicy(): ResumeSharingPolicy =
        if (this) {
            ResumeSharingPolicy.ORIGINAL_AFTER_CONFIRMATION
        } else {
            ResumeSharingPolicy.AI_SUMMARY_ONLY
        }

    private fun RoomEntity.toMeetingPlace(): MeetingPlace =
        when (meetingType) {
            MeetingType.ONLINE -> {
                check(sigunguId == null) {
                    "온라인 룸에는 sigunguId가 없어야 합니다. roomId=$id"
                }
                MeetingPlace.Online
            }

            MeetingType.OFFLINE -> {
                MeetingPlace.Offline(
                    sigunguId = checkNotNull(sigunguId) {
                        "오프라인 룸에는 sigunguId가 필요합니다. roomId=$id"
                    },
                )
            }
        }

    private fun MeetingPlace.toEntityValues(): Pair<MeetingType, Long?> =
        when (this) {
            MeetingPlace.Online ->
                MeetingType.ONLINE to null

            is MeetingPlace.Offline ->
                MeetingType.OFFLINE to sigunguId
        }
}