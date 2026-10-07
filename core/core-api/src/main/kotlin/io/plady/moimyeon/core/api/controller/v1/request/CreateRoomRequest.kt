package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.RoomCapacity
import io.plady.moimyeon.core.domain.room.RoomCreationCommand
import io.plady.moimyeon.core.domain.room.RoomDescription
import io.plady.moimyeon.core.domain.room.RoomSchedule
import io.plady.moimyeon.core.domain.room.RoomTitle
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class CreateRoomRequest(
    // --- 기본 정보(§4.1) ---
    val postingId: Long,
    val jobRoleId: Long,
    val round: String,
    val type: String? = null,
    // --- 진행 방식(§4.2) ---
    val method: String,
    val sigunguId: Long? = null,
    // --- 모집 인원(§4.3) ---
    val minParticipants: Int,
    val maxParticipants: Int,
    // --- 진행 일정(§4.4) ---
    val schedule: RoomScheduleRequest,
    // --- 소개(§4.1) ---
    val title: String,
    val description: String? = null,
    // --- 이력서(§4.5) ---
    val resumeId: UUID,
    val resumePublic: Boolean = false,
) {
    fun toCommand(): RoomCreationCommand = RoomCreationCommand(
        jobPostingId = postingId,
        jobRoleId = jobRoleId,
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
        resumeSharingPolicy =
        if (resumePublic) ResumeSharingPolicy.ORIGINAL_AFTER_CONFIRMATION else ResumeSharingPolicy.AI_SUMMARY_ONLY,
        resumeId = resumeId,
    )
}

data class RoomScheduleRequest(
    val date: LocalDate,
    val startTime: LocalTime,
    val durationMinutes: Int,
)

internal fun resolveMeetingPlace(method: String, sigunguId: Long?): MeetingPlace = when (parseRoomEnum<MeetingType>(method)) {
    MeetingType.ONLINE -> {
        if (sigunguId != null) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        MeetingPlace.Online
    }

    MeetingType.OFFLINE ->
        MeetingPlace.Offline(
            sigunguId = sigunguId ?: throw CoreApiException(CoreApiErrorType.INVALID_REQUEST),
        )
}

internal inline fun <reified E : Enum<E>> parseRoomEnum(value: String): E = try {
    enumValueOf<E>(value)
} catch (e: IllegalArgumentException) {
    throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
}
