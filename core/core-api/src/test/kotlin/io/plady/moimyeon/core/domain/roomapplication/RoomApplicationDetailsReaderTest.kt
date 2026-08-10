package io.plady.moimyeon.core.domain.roomapplication

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.member.Nickname
import io.plady.moimyeon.core.domain.resume.ResumeFinder
import io.plady.moimyeon.core.domain.resume.ResumeSummary
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomApplicationDetailsReaderTest {
    private val roomApplicationRepository = mockk<RoomApplicationRepository>()
    private val resumeSubmissionRepository = mockk<ResumeSubmissionRepository>()
    private val memberFinder = mockk<MemberFinder>()
    private val resumeFinder = mockk<ResumeFinder>()
    private val reader = RoomApplicationDetailsReader(
        roomApplicationRepository,
        resumeSubmissionRepository,
        memberFinder,
        resumeFinder,
    )

    private val roomId = UUID.randomUUID()
    private val applicantMemberId = UUID.randomUUID()
    private val resumeId = UUID.randomUUID()
    private val appliedAt = LocalDateTime.of(2026, 8, 5, 10, 0)

    @Test
    fun `철회되지 않은 여러 신청의 상세 정보를 배치 조회해 오래된 순서로 조립한다`() {
        val otherMemberId = UUID.randomUUID()
        val otherResumeId = UUID.randomUUID()
        val otherAppliedAt = appliedAt.plusMinutes(1)
        val firstApplication = application()
        val secondApplication = application(2L, otherMemberId, "함께 연습하고 싶어요.", otherAppliedAt)
        every {
            roomApplicationRepository.findByRoomIdAndStatusNotAndDeletedAtIsNullOrderByAppliedAtAsc(
                roomId,
                RoomApplicationStatus.WITHDRAWN,
            )
        } returns listOf(firstApplication, secondApplication)
        every {
            resumeSubmissionRepository.findByRoomApplicationIdInAndDeletedAtIsNull(listOf(1L, 2L))
        } returns listOf(submission(), submission(2L, otherResumeId))
        every { memberFinder.getAllByIds(listOf(applicantMemberId, otherMemberId)) } returns listOf(
            applicant(applicantMemberId, "성실한 다람쥐 12"),
            applicant(otherMemberId, "차분한 수달 03"),
        )
        every { resumeFinder.getSummaries(listOf(resumeId, otherResumeId)) } returns mapOf(
            resumeId to ResumeSummary(ResumeSummaryStatus.DONE, "결제 도메인 경험이 있는 백엔드 개발자"),
            otherResumeId to ResumeSummary(ResumeSummaryStatus.DONE, "데이터 파이프라인 경험이 있는 개발자"),
        )

        val result = reader.getAllByRoom(roomId)

        assertThat(result).containsExactly(
            RoomApplicationDetails(
                applicationId = 1L,
                applicant = ApplicationApplicant.Active(
                    memberId = applicantMemberId,
                    nickname = "성실한 다람쥐 12",
                ),
                note = firstApplication.note,
                resumeSummary = ApplicationResumeSummary.Ready("결제 도메인 경험이 있는 백엔드 개발자"),
                status = RoomApplicationStatus.PENDING,
                appliedAt = appliedAt,
            ),
            RoomApplicationDetails(
                applicationId = 2L,
                applicant = ApplicationApplicant.Active(
                    memberId = otherMemberId,
                    nickname = "차분한 수달 03",
                ),
                note = secondApplication.note,
                resumeSummary = ApplicationResumeSummary.Ready("데이터 파이프라인 경험이 있는 개발자"),
                status = RoomApplicationStatus.PENDING,
                appliedAt = otherAppliedAt,
            ),
        )
        verify(exactly = 1) {
            resumeSubmissionRepository.findByRoomApplicationIdInAndDeletedAtIsNull(listOf(1L, 2L))
        }
        verify(exactly = 1) { memberFinder.getAllByIds(listOf(applicantMemberId, otherMemberId)) }
        verify(exactly = 1) { resumeFinder.getSummaries(listOf(resumeId, otherResumeId)) }
    }

    @Test
    fun `이력서 요약이 처리 중이거나 실패했으면 요약 준비 중으로 제공한다`() {
        val application = application()
        every {
            roomApplicationRepository.findByRoomIdAndStatusNotAndDeletedAtIsNullOrderByAppliedAtAsc(any(), any())
        } returns listOf(application)
        every {
            resumeSubmissionRepository.findByRoomApplicationIdInAndDeletedAtIsNull(listOf(1L))
        } returns listOf(submission())
        every { memberFinder.getAllByIds(listOf(applicantMemberId)) } returns listOf(applicant(applicantMemberId, "성실한 다람쥐 12"))
        listOf(ResumeSummaryStatus.PROCESSING, ResumeSummaryStatus.FAILED).forEach { status ->
            every { resumeFinder.getSummaries(listOf(resumeId)) } returns mapOf(
                resumeId to ResumeSummary(status, null),
            )

            assertThat(reader.getAllByRoom(roomId).single().resumeSummary)
                .isEqualTo(ApplicationResumeSummary.Preparing)
        }
    }

    @Test
    fun `전달 사항이 없으면 빈 문자열로 제공한다`() {
        val application = application(applicationNote = "")
        every {
            roomApplicationRepository.findByRoomIdAndStatusNotAndDeletedAtIsNullOrderByAppliedAtAsc(any(), any())
        } returns listOf(application)
        every {
            resumeSubmissionRepository.findByRoomApplicationIdInAndDeletedAtIsNull(listOf(1L))
        } returns listOf(submission())
        every { memberFinder.getAllByIds(listOf(applicantMemberId)) } returns listOf(applicant(applicantMemberId, "성실한 다람쥐 12"))
        every { resumeFinder.getSummaries(listOf(resumeId)) } returns mapOf(
            resumeId to ResumeSummary(ResumeSummaryStatus.DONE, "결제 도메인 경험이 있는 백엔드 개발자"),
        )

        assertThat(reader.getAllByRoom(roomId).single().note).isEmpty()
    }

    @Test
    fun `탈퇴한 신청자는 신청 기록을 유지하고 공개 정보를 익명화한다`() {
        every {
            roomApplicationRepository.findByRoomIdAndStatusNotAndDeletedAtIsNullOrderByAppliedAtAsc(any(), any())
        } returns listOf(application())
        every {
            resumeSubmissionRepository.findByRoomApplicationIdInAndDeletedAtIsNull(listOf(1L))
        } returns listOf(submission())
        every { memberFinder.getAllByIds(listOf(applicantMemberId)) } returns emptyList()
        every { resumeFinder.getSummaries(listOf(resumeId)) } returns mapOf(
            resumeId to ResumeSummary(ResumeSummaryStatus.DONE, "결제 도메인 경험이 있는 백엔드 개발자"),
        )

        assertThat(reader.getAllByRoom(roomId).single().applicant)
            .isEqualTo(ApplicationApplicant.Withdrawn(applicantMemberId))
    }

    @Test
    fun `신청이 없으면 신청자와 이력서 정보를 조회하지 않는다`() {
        every {
            roomApplicationRepository.findByRoomIdAndStatusNotAndDeletedAtIsNullOrderByAppliedAtAsc(
                roomId,
                RoomApplicationStatus.WITHDRAWN,
            )
        } returns emptyList()

        assertThat(reader.getAllByRoom(roomId)).isEmpty()

        verify(exactly = 0) { resumeSubmissionRepository.findByRoomApplicationIdInAndDeletedAtIsNull(any()) }
        verify(exactly = 0) { memberFinder.getAllByIds(any()) }
        verify(exactly = 0) { resumeFinder.getSummaries(any()) }
    }

    private fun application(
        applicationId: Long = 1L,
        memberId: UUID = applicantMemberId,
        applicationNote: String = "실전처럼 연습하고 싶어요.",
        applicationAppliedAt: LocalDateTime = appliedAt,
    ): RoomApplicationEntity {
        return mockk {
            every { id } returns applicationId
            every { applicantMemberId } returns memberId
            every { note } returns applicationNote
            every { status } returns RoomApplicationStatus.PENDING
            every { appliedAt } returns applicationAppliedAt
        }
    }

    private fun submission(
        applicationId: Long = 1L,
        submittedResumeId: UUID = resumeId,
    ): ResumeSubmissionEntity {
        return mockk {
            every { roomApplicationId } returns applicationId
            every { sourceResumeId } returns submittedResumeId
        }
    }

    private fun applicant(memberId: UUID, nickname: String): Member {
        val member = mockk<Member>()
        every { member.id } returns memberId
        every { member.nickname } returns Nickname(nickname)
        return member
    }
}
