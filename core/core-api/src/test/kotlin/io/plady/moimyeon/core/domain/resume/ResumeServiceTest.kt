package io.plady.moimyeon.core.domain.resume

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ResumeServiceTest {
    private val resumeFinder = mockk<ResumeFinder>()
    private val fileStorage = mockk<ResumeFileStorage>()
    private val resumeManager = mockk<ResumeManager>()
    private val resumeRegistrar = mockk<ResumeRegistrar>()
    private val resumeSummarizer = mockk<ResumeSummarizer>()
    private val resumeService = ResumeService(resumeFinder, fileStorage, resumeManager, resumeRegistrar, resumeSummarizer)

    @Test
    fun `회원의 이력서 보관함을 조회한다`() {
        val memberId = UUID.randomUUID()
        val vault = mockk<ResumeVault>()
        every { resumeFinder.getVault(memberId) } returns vault

        val result = resumeService.getVault(memberId)

        assertThat(result).isSameAs(vault)
        verify(exactly = 1) { resumeFinder.getVault(memberId) }
    }

    @Test
    fun `이력서를 기본으로 지정한다`() {
        val memberId = UUID.randomUUID()
        val resumeId = UUID.randomUUID()
        every { resumeManager.makeDefault(memberId, resumeId) } just Runs

        resumeService.makeDefault(memberId, resumeId)

        verify(exactly = 1) { resumeManager.makeDefault(memberId, resumeId) }
    }

    @Test
    fun `이력서를 보관함 목록에서 숨긴다`() {
        val memberId = UUID.randomUUID()
        val resumeId = UUID.randomUUID()
        every { resumeManager.hide(memberId, resumeId, any()) } just Runs

        resumeService.hide(memberId, resumeId)

        verify(exactly = 1) { resumeManager.hide(memberId, resumeId, any()) }
    }

    @Test
    fun `이름과 PDF를 등록하면 원본과 이력서를 보관하고 AI 요약을 시작한다`() {
        val memberId = UUID.randomUUID()
        val resumeId = UUID.randomUUID()
        val registration = ResumeRegistration(
            name = "백엔드 지원용 이력서",
            upload = ResumeUpload(
                originalName = "resume.pdf",
                contentType = "application/pdf",
                content = "pdf-content".toByteArray(),
            ),
        )
        val storedFile = ResumeFile(
            key = "resumes/$memberId/$resumeId.pdf",
            originalName = registration.upload.originalName,
            sizeBytes = registration.upload.content.size.toLong(),
            contentType = registration.upload.contentType,
        )
        every { resumeRegistrar.validateCapacity(memberId) } just Runs
        every { fileStorage.store(memberId, registration.upload) } returns storedFile
        every { resumeRegistrar.register(memberId, registration.name, storedFile) } returns resumeId
        every { resumeSummarizer.summarize(resumeId) } just Runs

        val registeredResumeId = resumeService.register(memberId, registration)

        assertThat(registeredResumeId).isEqualTo(resumeId)
        verifyOrder {
            resumeRegistrar.validateCapacity(memberId)
            fileStorage.store(memberId, registration.upload)
            resumeRegistrar.register(memberId, registration.name, storedFile)
            resumeSummarizer.summarize(resumeId)
        }
    }

    @Test
    fun `이력서가 10개면 원본 파일을 보관하기 전에 등록을 거절한다`() {
        val memberId = UUID.randomUUID()
        val registration = ResumeRegistration(
            name = "백엔드 지원용 이력서",
            upload = ResumeUpload(
                originalName = "resume.pdf",
                contentType = "application/pdf",
                content = "pdf-content".toByteArray(),
            ),
        )
        every {
            resumeRegistrar.validateCapacity(memberId)
        } throws CoreException(CoreErrorType.RESUME_LIMIT_EXCEEDED)

        assertThatThrownBy { resumeService.register(memberId, registration) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_LIMIT_EXCEEDED)
            }
        verify(exactly = 0) { fileStorage.store(any(), any()) }
        verify(exactly = 0) { resumeRegistrar.register(any(), any(), any()) }
        verify(exactly = 0) { resumeSummarizer.summarize(any()) }
    }
}
