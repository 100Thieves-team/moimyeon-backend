package io.plady.moimyeon.core.api.facade

import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.catalog.JobRole
import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.domain.jobposting.JobPostingRef
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import io.plady.moimyeon.core.domain.participation.RoomParticipantService
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.room.RoomCapacity
import io.plady.moimyeon.core.domain.room.RoomSchedule
import io.plady.moimyeon.core.domain.room.RoomService
import io.plady.moimyeon.core.domain.room.RoomSummariesByStatus
import io.plady.moimyeon.core.domain.room.RoomSummary
import io.plady.moimyeon.core.domain.room.RoomTitle
import io.plady.moimyeon.core.domain.roomapplication.PendingRoomApplication
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionService
import io.plady.moimyeon.core.domain.trust.RoomReviewStatus
import io.plady.moimyeon.core.domain.trust.RoomReviewSummary
import io.plady.moimyeon.core.domain.trust.TrustService
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
    private val trustService = mockk<TrustService>()
    private val jobPostingService = mockk<JobPostingService>()
    private val companyService = mockk<CompanyService>()
    private val catalogService = mockk<CatalogService>()
    private val facade = InterviewOverviewFacade(
        applicationService,
        participantService,
        roomService,
        trustService,
        jobPostingService,
        companyService,
        catalogService,
    )

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
        val participatingRoomIds = listOf(participatingRoom.id, completedRoom.id)
        val roomSummariesByStatus = RoomSummariesByStatus(
            active = listOf(RoomSummary(participatingRoom, participantCount = 4)),
            completed = listOf(RoomSummary(completedRoom, participantCount = 3)),
        )
        val reviewSummaries = mapOf(
            completedRoom.id to RoomReviewSummary(
                roomId = completedRoom.id,
                attendedParticipantCount = 2,
                status = RoomReviewStatus.WRITABLE,
            ),
        )
        every { applicationService.getPendingApplications(memberId) } returns pendingApplications
        every { roomService.getRoomSummaries(listOf(pendingRoom.id)) } returns pendingRoomSummaries
        every { participantService.getParticipatingRoomIds(memberId) } returns participatingRoomIds
        every { roomService.getRoomSummariesByStatus(participatingRoomIds) } returns roomSummariesByStatus
        every { trustService.getRoomReviewSummaries(memberId, listOf(completedRoom.id)) } returns reviewSummaries
        every { jobPostingService.getRefs(setOf(1L)) } returns
            listOf(JobPostingRef(id = 1L, companyId = 1L, postingName = "백엔드 개발자 채용"))
        every { companyService.getCompanies(setOf(1L)) } returns listOf(Company(id = 1L, name = "달빛페이"))
        every { catalogService.getJobRoles(setOf(2L)) } returns
            listOf(JobRole(id = 2L, code = "BACKEND", displayName = "서버·백엔드"))
        every { catalogService.getRegionLabels(emptySet()) } returns emptyList()

        val result = facade.getOverview(memberId)

        assertThat(result.pendingApplications.single().applicationId).isEqualTo(1L)
        assertThat(result.pendingApplications.single().room.participantCount).isEqualTo(3)
        assertThat(result.participatingRooms.single().room.participantCount).isEqualTo(4)
        assertThat(result.completedRooms.single().room.participantCount).isEqualTo(2)
        assertThat(result.completedRooms.single().reviewStatus).isEqualTo("WRITABLE")
        // 표시명은 세 구분 공통 참조에서 붙는다(MOI-496). 온라인 룸이라 region 은 비어 있다.
        assertThat(result.participatingRooms.single().room.company?.name).isEqualTo("달빛페이")
        assertThat(result.participatingRooms.single().room.jobPosting?.postingName).isEqualTo("백엔드 개발자 채용")
        assertThat(result.participatingRooms.single().room.jobRole?.displayName).isEqualTo("서버·백엔드")
        assertThat(result.participatingRooms.single().room.region).isNull()
        verifyOrder {
            applicationService.getPendingApplications(memberId)
            roomService.getRoomSummaries(listOf(pendingRoom.id))
            participantService.getParticipatingRoomIds(memberId)
            roomService.getRoomSummariesByStatus(participatingRoomIds)
            trustService.getRoomReviewSummaries(memberId, listOf(completedRoom.id))
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
