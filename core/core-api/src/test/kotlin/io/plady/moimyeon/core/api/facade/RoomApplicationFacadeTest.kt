package io.plady.moimyeon.core.api.facade

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.v1.response.ApplicantJobRoleResponse
import io.plady.moimyeon.core.api.controller.v1.response.ApplicationAiSummaryResponse
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.catalog.JobGroup
import io.plady.moimyeon.core.domain.catalog.JobRole
import io.plady.moimyeon.core.domain.profile.MemberProfile
import io.plady.moimyeon.core.domain.profile.ProfileService
import io.plady.moimyeon.core.domain.roomapplication.ApplicationApplicant
import io.plady.moimyeon.core.domain.roomapplication.ApplicationResumeSummary
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationDetails
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionService
import io.plady.moimyeon.core.enums.MeetingPreference
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomApplicationFacadeTest {
    private val roomApplicationSubmissionService = mockk<RoomApplicationSubmissionService>()
    private val profileService = mockk<ProfileService>()
    private val catalogService = mockk<CatalogService>()
    private val facade = RoomApplicationFacade(
        roomApplicationSubmissionService = roomApplicationSubmissionService,
        profileService = profileService,
        catalogService = catalogService,
    )

    private val hostMemberId = UUID.randomUUID()
    private val applicantMemberId = UUID.randomUUID()
    private val roomId = UUID.randomUUID()
    private val appliedAt = LocalDateTime.of(2026, 8, 5, 10, 0)

    @Test
    fun `새 참가 신청 검토 흐름에 프로필의 관심 직무 목록을 조립한다`() {
        every { roomApplicationSubmissionService.getApplications(hostMemberId, roomId) } returns listOf(details())
        every { profileService.getProfiles(listOf(applicantMemberId)) } returns listOf(profile(listOf(102L, 101L)))
        every { catalogService.getJobCatalog() } returns listOf(
            JobGroup(
                id = 1L,
                code = "DEVELOPMENT",
                displayName = "개발",
                roles = listOf(
                    JobRole(101L, "BACKEND", "백엔드 개발"),
                    JobRole(102L, "DATA_ENGINEER", "데이터 엔지니어"),
                ),
            ),
        )

        val application = facade.getApplications(hostMemberId, roomId).applications.single()

        assertThat(application.applicant.jobRoles).containsExactly(
            ApplicantJobRoleResponse(101L, "백엔드 개발"),
            ApplicantJobRoleResponse(102L, "데이터 엔지니어"),
        )
        assertThat(application.applicant.activitySummary).isNull()
        assertThat(application.aiSummary).isEqualTo(
            ApplicationAiSummaryResponse("DONE", "결제 도메인 경험이 있는 백엔드 개발자"),
        )
        verify(exactly = 1) { profileService.getProfiles(listOf(applicantMemberId)) }
    }

    @Test
    fun `탈퇴한 신청자는 프로필을 조회하지 않고 익명화한다`() {
        every {
            roomApplicationSubmissionService.getApplications(hostMemberId, roomId)
        } returns listOf(details(ApplicationApplicant.Withdrawn(applicantMemberId)))

        val applicant = facade.getApplications(hostMemberId, roomId).applications.single().applicant

        assertThat(applicant.nickname).isEqualTo("탈퇴한 사용자")
        assertThat(applicant.jobRoles).isEmpty()
        assertThat(applicant.activitySummary).isNull()
        verify(exactly = 0) { profileService.getProfiles(any()) }
        verify(exactly = 0) { catalogService.getJobCatalog() }
    }

    private fun details(
        applicant: ApplicationApplicant = ApplicationApplicant.Active(
            memberId = applicantMemberId,
            nickname = "성실한 다람쥐 12",
        ),
    ): RoomApplicationDetails {
        return RoomApplicationDetails(
            applicationId = 1L,
            applicant = applicant,
            note = "실전처럼 연습하고 싶어요.",
            resumeSummary = ApplicationResumeSummary.Ready("결제 도메인 경험이 있는 백엔드 개발자"),
            status = RoomApplicationStatus.PENDING,
            appliedAt = appliedAt,
        )
    }

    private fun profile(jobRoleIds: List<Long>): MemberProfile {
        return MemberProfile(
            memberId = applicantMemberId,
            bio = "",
            meetingPreference = MeetingPreference.UNSPECIFIED,
            sigunguId = null,
            interestJobRoleIds = jobRoleIds,
            interestCompanyIds = emptyList(),
        )
    }
}
