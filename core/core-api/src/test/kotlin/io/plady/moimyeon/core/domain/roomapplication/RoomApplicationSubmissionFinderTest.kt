package io.plady.moimyeon.core.domain.roomapplication

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.resume.ResumeFinder
import io.plady.moimyeon.core.domain.resume.ResumeSummary
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomApplicationSubmissionFinderTest {
    private val roomApplicationRepository = mockk<RoomApplicationRepository>()
    private val resumeSubmissionRepository = mockk<ResumeSubmissionRepository>()
    private val resumeFinder = mockk<ResumeFinder>()
    private val finder = RoomApplicationSubmissionFinder(
        roomApplicationRepository,
        resumeSubmissionRepository,
        resumeFinder,
    )

    private val applicantMemberId = UUID.randomUUID()
    private val roomId = UUID.randomUUID()
    private val resumeId = UUID.randomUUID()
    private val appliedAt = LocalDateTime.of(2026, 8, 5, 14, 30)

    // 철회 후 재신청 하는 경우가 있기에 필요한 케이스!
    @Test
    fun `가장 최근 신청과 그 신청에 제출한 이력서 및 현재 AI 요약을 조회한다`() {
        val applicationId = 2L
        val application = applicationEntity(applicationId)
        val submission = submissionEntity()
        val summary = ResumeSummary(ResumeSummaryStatus.PROCESSING, null)
        every {
            roomApplicationRepository
                .findFirstByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                    roomId,
                    applicantMemberId,
                )
        } returns application
        every {
            resumeSubmissionRepository.findByRoomApplicationIdAndDeletedAtIsNull(applicationId)
        } returns submission
        every { resumeFinder.getSummary(applicantMemberId, resumeId) } returns summary

        val result = finder.getLatestByApplicant(applicantMemberId, roomId)

        assertThat(result).isEqualTo(
            RoomApplication(
                id = applicationId,
                roomId = roomId,
                applicantMemberId = applicantMemberId,
                note = "백엔드 면접을 실전처럼 연습하고 싶어요.",
                resumeSubmission = ResumeSubmission(
                    sourceResumeId = resumeId,
                    file = ResumeFile(
                        key = "resumes/$applicantMemberId/source.pdf",
                        originalName = "backend.pdf",
                        sizeBytes = 1024L,
                        contentType = "application/pdf",
                    ),
                ),
                resumeSummary = summary,
                status = RoomApplicationStatus.PENDING,
                appliedAt = appliedAt,
            ),
        )
        verifyOrder {
            roomApplicationRepository
                .findFirstByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                    roomId,
                    applicantMemberId,
                )
            resumeSubmissionRepository.findByRoomApplicationIdAndDeletedAtIsNull(applicationId)
            resumeFinder.getSummary(applicantMemberId, resumeId)
        }
    }

    @Test
    fun `전달 사항이 없는 신청은 빈 문자열로 조회한다`() {
        val applicationId = 2L
        every {
            roomApplicationRepository
                .findFirstByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                    roomId,
                    applicantMemberId,
                )
        } returns applicationEntity(applicationId, "")
        every {
            resumeSubmissionRepository.findByRoomApplicationIdAndDeletedAtIsNull(applicationId)
        } returns submissionEntity()
        every { resumeFinder.getSummary(applicantMemberId, resumeId) } returns ResumeSummary(
            ResumeSummaryStatus.DONE,
            "백엔드 개발자",
        )

        assertThat(finder.getLatestByApplicant(applicantMemberId, roomId).note).isEmpty()
    }

    @Test
    fun `해당 룸에 자신의 신청이 없으면 APPLICATION_NOT_FOUND를 던진다`() {
        every {
            roomApplicationRepository
                .findFirstByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                    roomId,
                    applicantMemberId,
                )
        } returns null

        assertThatThrownBy {
            finder.getLatestByApplicant(applicantMemberId, roomId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.APPLICATION_NOT_FOUND)
        }
        verify(exactly = 0) {
            resumeSubmissionRepository.findByRoomApplicationIdAndDeletedAtIsNull(any())
        }
        verify(exactly = 0) { resumeFinder.getSummary(any(), any()) }
    }

    @Test
    fun `신청에 연결된 제출 이력서가 없으면 저장 불변식 위반으로 실패한다`() {
        val applicationId = 2L
        val application = mockk<RoomApplicationEntity> {
            every { id } returns applicationId
        }
        every {
            roomApplicationRepository
                .findFirstByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                    roomId,
                    applicantMemberId,
                )
        } returns application
        every {
            resumeSubmissionRepository.findByRoomApplicationIdAndDeletedAtIsNull(applicationId)
        } returns null

        assertThatThrownBy {
            finder.getLatestByApplicant(applicantMemberId, roomId)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("applicationId=$applicationId")
        verify(exactly = 0) { resumeFinder.getSummary(any(), any()) }
    }

    private fun applicationEntity(
        applicationId: Long,
        applicationNote: String = "백엔드 면접을 실전처럼 연습하고 싶어요.",
    ): RoomApplicationEntity {
        return mockk {
            every { id } returns applicationId
            every { roomId } returns this@RoomApplicationSubmissionFinderTest.roomId
            every { applicantMemberId } returns this@RoomApplicationSubmissionFinderTest.applicantMemberId
            every { note } returns applicationNote
            every { status } returns RoomApplicationStatus.PENDING
            every { appliedAt } returns this@RoomApplicationSubmissionFinderTest.appliedAt
        }
    }

    private fun submissionEntity(): ResumeSubmissionEntity {
        return mockk {
            every { sourceResumeId } returns resumeId
            every { fileKey } returns "resumes/$applicantMemberId/source.pdf"
            every { originalName } returns "backend.pdf"
            every { sizeBytes } returns 1024L
            every { contentType } returns "application/pdf"
        }
    }
}
