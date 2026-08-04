package io.plady.moimyeon.core.domain.roomapplication

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.resume.ResumeSummary
import io.plady.moimyeon.core.domain.resume.ResumeValidator
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomApplicationServiceTest {
    private val roomApplicationManager = mockk<RoomApplicationManager>()
    private val resumeValidator = mockk<ResumeValidator>()
    private val roomApplicationFinder = mockk<RoomApplicationFinder>()
    private val service = RoomApplicationService(
        roomApplicationManager,
        resumeValidator,
        roomApplicationFinder,
    )

    private val applicantMemberId = UUID.randomUUID()
    private val roomId = UUID.randomUUID()
    private val resumeId = UUID.randomUUID()
    private val applicationForm = RoomApplicationForm(resumeId, "백엔드 면접을 실전처럼 연습하고 싶어요.")
    private val appliedAt = LocalDateTime.of(2026, 8, 5, 14, 30)
    private val sourceFile = ResumeFile(
        key = "resumes/$applicantMemberId/source.pdf",
        originalName = "backend.pdf",
        sizeBytes = 1024L,
        contentType = "application/pdf",
    )

    @Test
    fun `보관 이력서를 선택하면 제출 시점 파일 참조와 함께 참가 신청을 제출한다`() {
        givenValidApplicantAndResume()
        every {
            roomApplicationManager.submit(
                applicantMemberId,
                roomId,
                applicationForm.note,
                ResumeSubmission(resumeId, sourceFile),
            )
        } returns 1L

        val applicationId = service.submit(applicantMemberId, roomId, applicationForm)

        assertThat(applicationId).isEqualTo(1L)
        verifyOrder {
            resumeValidator.validateOwnedBy(applicantMemberId, resumeId)
            roomApplicationManager.submit(
                applicantMemberId,
                roomId,
                applicationForm.note,
                ResumeSubmission(resumeId, sourceFile),
            )
        }
    }

    @Test
    fun `자신의 보관 이력서가 아니면 신청을 저장하지 않는다`() {
        every {
            resumeValidator.validateOwnedBy(applicantMemberId, resumeId)
        } throws CoreException(CoreErrorType.RESUME_NOT_FOUND)

        assertSubmissionFails(CoreErrorType.RESUME_NOT_FOUND)

        verify(exactly = 0) { roomApplicationManager.submit(any(), any(), any(), any()) }
    }

    @Test
    fun `신청 조건 때문에 신청 저장이 실패하면 실패를 그대로 전달한다`() {
        givenValidApplicantAndResume()
        every {
            roomApplicationManager.submit(any(), any(), any(), any())
        } throws CoreException(CoreErrorType.ROOM_NOT_RECRUITING)

        assertSubmissionFails(CoreErrorType.ROOM_NOT_RECRUITING)
    }

    @Test
    fun `신청자는 해당 룸에 제출한 자신의 신청을 조회한다`() {
        val application = RoomApplication(
            id = 1L,
            roomId = roomId,
            applicantMemberId = applicantMemberId,
            note = applicationForm.note,
            resumeSubmission = ResumeSubmission(resumeId, sourceFile),
            resumeSummary = ResumeSummary(
                status = ResumeSummaryStatus.DONE,
                content = "백엔드 개발 경험과 결제 도메인 경험이 있습니다.",
            ),
            status = RoomApplicationStatus.PENDING,
            appliedAt = appliedAt,
        )
        every { roomApplicationFinder.get(applicantMemberId, roomId) } returns application

        val result = service.get(applicantMemberId, roomId)

        assertThat(result).isEqualTo(application)
        verify(exactly = 1) { roomApplicationFinder.get(applicantMemberId, roomId) }
    }

    @Test
    fun `신청자는 방장이 처리하기 전 자신의 참가 신청을 철회한다`() {
        justRun { roomApplicationManager.withdraw(applicantMemberId, roomId) }

        service.withdraw(applicantMemberId, roomId)

        verify(exactly = 1) { roomApplicationManager.withdraw(applicantMemberId, roomId) }
    }

    @Test
    fun `해당 룸에 자신의 신청이 없으면 철회 실패를 그대로 전달한다`() {
        every {
            roomApplicationManager.withdraw(applicantMemberId, roomId)
        } throws CoreException(CoreErrorType.APPLICATION_NOT_FOUND)

        assertWithdrawalFails(CoreErrorType.APPLICATION_NOT_FOUND)
    }

    @Test
    fun `이미 처리된 신청이면 철회 실패를 그대로 전달한다`() {
        every {
            roomApplicationManager.withdraw(applicantMemberId, roomId)
        } throws CoreException(CoreErrorType.APPLICATION_ALREADY_HANDLED)

        assertWithdrawalFails(CoreErrorType.APPLICATION_ALREADY_HANDLED)
    }

    private fun givenValidApplicantAndResume() {
        every { resumeValidator.validateOwnedBy(applicantMemberId, resumeId) } returns sourceFile
    }

    private fun assertSubmissionFails(errorType: CoreErrorType) {
        assertThatThrownBy {
            service.submit(applicantMemberId, roomId, applicationForm)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(errorType)
        }
    }

    private fun assertWithdrawalFails(errorType: CoreErrorType) {
        assertThatThrownBy {
            service.withdraw(applicantMemberId, roomId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(errorType)
        }
    }
}
