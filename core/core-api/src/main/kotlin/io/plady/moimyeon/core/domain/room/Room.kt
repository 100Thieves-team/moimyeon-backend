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

    // 채용 공고와 직무
    val jobPostingId: Long,
    val jobRoleId: Long,

    // 룸 소개
    val title: RoomTitle,
    val description: RoomDescription?,

    // 면접 타입
    val interviewStage: InterviewStage,
    val interviewType: InterviewType?,

    // 모임 방식, 모집 인원수, 진행 일정
    val meetingPlace: MeetingPlace,
    val capacity: RoomCapacity,
    val schedule: RoomSchedule,

    // 이력서 공개 정책
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

    companion object {
        private val RESUME_ORIGINAL_OPEN_STATUSES = setOf(RoomStatus.CONFIRMED, RoomStatus.IN_PROGRESS)

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
         * 영속화된 기존 Room을 재구성한다.
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
