package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.enums.RoomStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomTest {
    @Test
    fun `원본 공개 룸은 확정부터 진행 중까지 원본이 열린다`() {
        val openStatuses = listOf(RoomStatus.CONFIRMED, RoomStatus.IN_PROGRESS)

        assertThat(openStatuses)
            .allMatch { room(ResumeSharingPolicy.ORIGINAL_AFTER_CONFIRMATION, it).opensResumeOriginal() }
    }

    @Test
    fun `원본 공개 룸이라도 확정 전이거나 끝난 상태에서는 원본이 열리지 않는다`() {
        val closedStatuses = listOf(RoomStatus.RECRUITING, RoomStatus.COMPLETED, RoomStatus.CANCELED)

        assertThat(closedStatuses)
            .noneMatch { room(ResumeSharingPolicy.ORIGINAL_AFTER_CONFIRMATION, it).opensResumeOriginal() }
    }

    @Test
    fun `원본 비공개 룸은 어느 상태에서도 원본이 열리지 않는다`() {
        assertThat(RoomStatus.entries)
            .noneMatch { room(ResumeSharingPolicy.AI_SUMMARY_ONLY, it).opensResumeOriginal() }
    }

    private fun room(resumeSharingPolicy: ResumeSharingPolicy, status: RoomStatus): Room = Room.reconstitute(
        id = UUID.randomUUID(),
        jobPostingId = 1L,
        jobRoleId = 1L,
        title = RoomTitle("백엔드 모의면접 함께 준비해요"),
        description = null,
        interviewStage = InterviewStage.FIRST,
        interviewType = InterviewType.JOB,
        meetingPlace = MeetingPlace.Online,
        capacity = RoomCapacity(min = 2, max = 6),
        schedule = RoomSchedule(startAt = LocalDateTime.of(2026, 9, 1, 19, 0), durationMinutes = 60),
        resumeSharingPolicy = resumeSharingPolicy,
        status = status,
    )
}
