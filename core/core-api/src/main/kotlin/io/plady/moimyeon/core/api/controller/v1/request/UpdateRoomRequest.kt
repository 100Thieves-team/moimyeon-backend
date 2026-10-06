package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.room.RoomCapacity
import io.plady.moimyeon.core.domain.room.RoomDescription
import io.plady.moimyeon.core.domain.room.RoomSchedule
import io.plady.moimyeon.core.domain.room.RoomTitle
import io.plady.moimyeon.core.domain.room.RoomUpdateCommand
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType

// 식별 참조(postingId/jobRoleId)와 이력서(resumeId/resumePublic)는 포함하지 않는다.
data class UpdateRoomRequest(
    val round: String,
    val type: String? = null,
    val method: String,
    val sigunguId: Long? = null,
    val minParticipants: Int,
    val maxParticipants: Int,
    val schedule: RoomScheduleRequest,
    val title: String,
    val description: String? = null,
) {
    fun toCommand(): RoomUpdateCommand = RoomUpdateCommand(
        title = RoomTitle(title),
        description = description?.let(::RoomDescription),
        interviewStage = parseRoomEnum<InterviewStage>(round),
        interviewType = type?.let { parseRoomEnum<InterviewType>(it) },
        meetingPlace = resolveMeetingPlace(method, sigunguId),
        capacity = RoomCapacity(min = minParticipants, max = maxParticipants),
        schedule = RoomSchedule(
            startAt = schedule.date.atTime(schedule.startTime),
            durationMinutes = schedule.durationMinutes,
        ),
    )
}
