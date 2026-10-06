package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import java.time.LocalDateTime
import java.util.UUID

class Room(
    val id: UUID,

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

    status: RoomStatus,
) {
    var status: RoomStatus = status
        private set

    // 원본 열람 창(「룸 참여」 §4.3, MOI-414 D3-6): 확정 이후 ~ 종료 전.
    // 명부의 canViewOriginal 과 발급 게이트가 이 판정을 공유한다 - 갈리면 버튼과 발급이 다른 말을 한다.
    fun opensResumeOriginal(): Boolean {
        return resumeSharingPolicy == ResumeSharingPolicy.ORIGINAL_AFTER_CONFIRMATION &&
            status in RESUME_ORIGINAL_OPEN_STATUSES
    }

    // 취소된 룸은 완료할 수 없어 출석 명단이 필요 없다.
    fun hasConfirmedRoster(): Boolean = status in CONFIRMED_ROSTER_STATUSES

    // 진행 기능은 MVP 공개 API에서 제외됐지만 기존 내부 컴포넌트의 상태 판정은 CONFIRMED 하나로 수렴시킨다.
    internal fun isProgressAvailable(at: LocalDateTime): Boolean = RoomProgressAvailability.isAvailable(status, schedule.startAt, at)

    companion object {
        private val RESUME_ORIGINAL_OPEN_STATUSES = setOf(RoomStatus.CONFIRMED)
        private val CONFIRMED_ROSTER_STATUSES = setOf(RoomStatus.CONFIRMED, RoomStatus.COMPLETED)

        fun create(
            id: UUID,
            jobPostingId: Long,
            jobRoleId: Long,
            title: RoomTitle,
            description: RoomDescription?,
            interviewStage: InterviewStage,
            interviewType: InterviewType?,
            meetingPlace: MeetingPlace,
            capacity: RoomCapacity,
            schedule: RoomSchedule,
            resumeSharingPolicy: ResumeSharingPolicy,
            now: LocalDateTime,
        ): Room {
            requireBusiness(
                schedule.startAt.isAfter(now),
                CoreErrorType.ROOM_START_AT_NOT_FUTURE,
            )

            return Room(
                id = id,
                jobPostingId = jobPostingId,
                jobRoleId = jobRoleId,
                title = title,
                description = description,
                interviewStage = interviewStage,
                interviewType = interviewType,
                meetingPlace = meetingPlace,
                capacity = capacity,
                schedule = schedule,
                resumeSharingPolicy = resumeSharingPolicy,
                status = RoomStatus.RECRUITING,
            )
        }

        /**
         * 애플리케이션의 신규 생성 경로에서는 사용하지 않는다.
         */
        internal fun reconstitute(
            id: UUID,
            jobPostingId: Long,
            jobRoleId: Long,
            title: RoomTitle,
            description: RoomDescription?,
            interviewStage: InterviewStage,
            interviewType: InterviewType?,
            meetingPlace: MeetingPlace,
            capacity: RoomCapacity,
            schedule: RoomSchedule,
            resumeSharingPolicy: ResumeSharingPolicy,
            status: RoomStatus,
        ): Room = Room(
            id = id,
            jobPostingId = jobPostingId,
            jobRoleId = jobRoleId,
            title = title,
            description = description,
            interviewStage = interviewStage,
            interviewType = interviewType,
            meetingPlace = meetingPlace,
            capacity = capacity,
            schedule = schedule,
            resumeSharingPolicy = resumeSharingPolicy,
            status = status,
        )
    }
}

internal object RoomProgressAvailability {
    fun isAvailable(status: RoomStatus, startAt: LocalDateTime, at: LocalDateTime): Boolean = status == RoomStatus.CONFIRMED &&
        RoomSchedule.isPassed(startAt, at)
}
