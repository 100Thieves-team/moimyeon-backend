package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import java.util.UUID

data class RoomCreationCommand(
    val jobPostingId: Long,
    val jobRoleId: Long,
    val title: RoomTitle,
    val description: RoomDescription?,
    val interviewStage: InterviewStage,
    val interviewType: InterviewType?,
    val meetingPlace: MeetingPlace,
    val capacity: RoomCapacity,
    val schedule: RoomSchedule,
    val resumeSharingPolicy: ResumeSharingPolicy,
    val resumeId: UUID,
)
