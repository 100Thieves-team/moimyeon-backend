package io.plady.moimyeon.core.domain.roomapplication

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.resume.ResumeValidator
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class RoomApplicationSubmissionServiceTest {
    private val roomApplicationSubmissionManager = mockk<RoomApplicationSubmissionManager>()
    private val resumeValidator = mockk<ResumeValidator>()
    private val service = RoomApplicationSubmissionService(
        roomApplicationSubmissionManager,
        resumeValidator,
    )

    private val applicantMemberId = UUID.randomUUID()
    private val roomId = UUID.randomUUID()
    private val resumeId = UUID.randomUUID()
    private val applicationForm = RoomApplicationForm(resumeId, "백엔드 면접을 실전처럼 연습하고 싶어요.")
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
            roomApplicationSubmissionManager.submit(
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
            roomApplicationSubmissionManager.submit(
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

        verify(exactly = 0) { roomApplicationSubmissionManager.submit(any(), any(), any(), any()) }
    }

    @Test
    fun `신청 조건 때문에 신청 저장이 실패하면 실패를 그대로 전달한다`() {
        givenValidApplicantAndResume()
        every {
            roomApplicationSubmissionManager.submit(any(), any(), any(), any())
        } throws CoreException(CoreErrorType.ROOM_NOT_RECRUITING)

        assertSubmissionFails(CoreErrorType.ROOM_NOT_RECRUITING)
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
}
