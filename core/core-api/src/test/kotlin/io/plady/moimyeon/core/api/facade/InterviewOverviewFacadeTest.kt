package io.plady.moimyeon.core.api.facade

import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.participation.CompletedRoomParticipation
import io.plady.moimyeon.core.domain.participation.ParticipatingRoom
import io.plady.moimyeon.core.domain.participation.RoomParticipantService
import io.plady.moimyeon.core.domain.participation.RoomParticipationHistory
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.room.RoomCapacity
import io.plady.moimyeon.core.domain.room.RoomSchedule
import io.plady.moimyeon.core.domain.room.RoomService
import io.plady.moimyeon.core.domain.room.RoomSummary
import io.plady.moimyeon.core.domain.room.RoomTitle
import io.plady.moimyeon.core.domain.roomapplication.PendingRoomApplication
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionService
import io.plady.moimyeon.core.domain.trust.RoomReviewStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.enums.RoomStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class InterviewOverviewFacadeTest {
    private val applicationService = mockk<RoomApplicationSubmissionService>()
    private val participantService = mockk<RoomParticipantService>()
    private val roomService = mockk<RoomService>()
    private val facade = InterviewOverviewFacade(applicationService, participantService, roomService)

    @Test
    fun `신청과 참여 영역의 결과를 화면 응답으로만 조립한다`() {
        val memberId = UUID.randomUUID()
        val pendingRoom = room(RoomStatus.RECRUITING)
        val participatingRoom = room(RoomStatus.CONFIRMED)
        val completedRoom = room(RoomStatus.COMPLETED)
        val pendingApplications = listOf(
            PendingRoomApplication(
                id = 1L,
                roomId = pendingRoom.id,
                resumeOriginalName = "backend.pdf",
                appliedAt = LocalDateTime.of(2026, 8, 5, 14, 30),
            ),
        )
        val pendingRoomSummaries = listOf(RoomSummary(pendingRoom, participantCount = 3))
        val history = RoomParticipationHistory(
            participatingRooms = listOf(ParticipatingRoom(participatingRoom, participantCount = 4)),
            completedRooms = listOf(
                CompletedRoomParticipation(
                    room = completedRoom,
                    attendedParticipantCount = 2,
                    reviewStatus = RoomReviewStatus.WRITABLE,
                ),
            ),
        )
        every { applicationService.getPendingApplications(memberId) } returns pendingApplications
        every { roomService.getRoomSummaries(listOf(pendingRoom.id)) } returns pendingRoomSummaries
        every { participantService.getParticipationHistory(memberId) } returns history

        val result = facade.getOverview(memberId)

        assertThat(result.pendingApplications.single().applicationId).isEqualTo(1L)
        assertThat(result.pendingApplications.single().room.participantCount).isEqualTo(3)
        assertThat(result.participatingRooms.single().room.participantCount).isEqualTo(4)
        assertThat(result.completedRooms.single().room.participantCount).isEqualTo(2)
        assertThat(result.completedRooms.single().reviewStatus).isEqualTo("WRITABLE")
        verifyOrder {
            applicationService.getPendingApplications(memberId)
            roomService.getRoomSummaries(listOf(pendingRoom.id))
            participantService.getParticipationHistory(memberId)
        }
    }

    private fun room(status: RoomStatus): Room {
        return Room(
            id = UUID.randomUUID(),
            jobPostingId = 1L,
            jobRoleId = 2L,
            title = RoomTitle("달빛페이 백엔드 1차 모의면접"),
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingPlace = MeetingPlace.Online,
            capacity = RoomCapacity(min = 3, max = 5),
            schedule = RoomSchedule(LocalDateTime.of(2026, 8, 20, 19, 0), 60),
            resumeSharingPolicy = ResumeSharingPolicy.AI_SUMMARY_ONLY,
            status = status,
        )
    }
}
