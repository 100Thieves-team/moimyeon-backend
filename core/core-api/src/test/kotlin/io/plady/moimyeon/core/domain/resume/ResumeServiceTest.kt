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
    fun `회원의 이력서 목록을 조회한다`() {
        val memberId = UUID.randomUUID()
        val resumes = listOf(mockk<Resume>())
        every { resumeFinder.getAll(memberId) } returns resumes

        val result = resumeService.getAll(memberId)

        assertThat(result).isSameAs(resumes)
        verify(exactly = 1) { resumeFinder.getAll(memberId) }
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
    fun `이력서 이름을 변경한다`() {
        val memberId = UUID.randomUUID()
        val resumeId = UUID.randomUUID()
        val name = "데이터 엔지니어 지원용"
        every { resumeManager.rename(memberId, resumeId, name) } just Runs

        resumeService.rename(memberId, resumeId, name)

        verify(exactly = 1) { resumeManager.rename(memberId, resumeId, name) }
    }

    @Test
    fun `이름을 변경할 수 없는 이력서이면 실패를 그대로 전달한다`() {
        val memberId = UUID.randomUUID()
        val resumeId = UUID.randomUUID()
        every {
            resumeManager.rename(memberId, resumeId, "새 이름")
        } throws CoreException(CoreErrorType.RESUME_NOT_FOUND)

        assertThatThrownBy { resumeService.rename(memberId, resumeId, "새 이름") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_NOT_FOUND)
            }
    }

    @Test
    fun `이력서를 삭제한다`() {
        val memberId = UUID.randomUUID()
        val resumeId = UUID.randomUUID()
        every { resumeManager.delete(memberId, resumeId, any()) } just Runs

        resumeService.delete(memberId, resumeId)

        verify(exactly = 1) { resumeManager.delete(memberId, resumeId, any()) }
    }

    @Test
    fun `PDF를 등록하면 파일명으로 신규 이력서를 보관하고 AI 요약을 시작한다`() {
        val memberId = UUID.randomUUID()
        val resumeId = UUID.randomUUID()
        val upload = ResumeUpload(
            originalName = "resume.pdf",
            contentType = "application/pdf",
            content = "pdf-content".toByteArray(),
        )
        val storedFile = ResumeFile(
            key = "resumes/$memberId/$resumeId.pdf",
            originalName = upload.originalName,
            sizeBytes = upload.content.size.toLong(),
            contentType = upload.contentType,
        )
        val newResume = NewResume(storedFile.originalName, storedFile)
        every { resumeRegistrar.validateCapacity(memberId) } just Runs
        every { fileStorage.store(memberId, upload) } returns storedFile
        every { resumeRegistrar.register(memberId, newResume) } returns resumeId
        every { resumeSummarizer.summarize(resumeId) } just Runs

        val registeredResumeId = resumeService.register(memberId, upload)

        assertThat(registeredResumeId).isEqualTo(resumeId)
        verifyOrder {
            resumeRegistrar.validateCapacity(memberId)
            fileStorage.store(memberId, upload)
            resumeRegistrar.register(memberId, newResume)
            resumeSummarizer.summarize(resumeId)
        }
    }

    @Test
    fun `이력서가 10개면 원본 파일을 보관하기 전에 등록을 거절한다`() {
        val memberId = UUID.randomUUID()
        val upload = ResumeUpload(
            originalName = "resume.pdf",
            contentType = "application/pdf",
            content = "pdf-content".toByteArray(),
        )
        every {
            resumeRegistrar.validateCapacity(memberId)
        } throws CoreException(CoreErrorType.RESUME_LIMIT_EXCEEDED)

        assertThatThrownBy { resumeService.register(memberId, upload) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_LIMIT_EXCEEDED)
            }
        verify(exactly = 0) { fileStorage.store(any(), any()) }
        verify(exactly = 0) { resumeRegistrar.register(any(), any()) }
        verify(exactly = 0) { resumeSummarizer.summarize(any()) }
    }

    @Test
    fun `원본 파일 저장에 실패하면 이력서를 등록하거나 AI 요약을 시작하지 않는다`() {
        val memberId = UUID.randomUUID()
        val upload = resumeUpload()
        every { resumeRegistrar.validateCapacity(memberId) } just Runs
        every { fileStorage.store(memberId, upload) } throws IllegalStateException("file storage unavailable")

        assertThatThrownBy { resumeService.register(memberId, upload) }
            .isInstanceOf(IllegalStateException::class.java)
        verify(exactly = 0) { resumeRegistrar.register(any(), any()) }
        verify(exactly = 0) { resumeSummarizer.summarize(any()) }
    }

    @Test
    fun `원본 파일 저장 후 DB 등록에 실패하면 AI 요약을 시작하지 않는다`() {
        val memberId = UUID.randomUUID()
        val upload = resumeUpload()
        val storedFile = resumeFile(memberId, upload)
        val newResume = NewResume(storedFile.originalName, storedFile)
        every { resumeRegistrar.validateCapacity(memberId) } just Runs
        every { fileStorage.store(memberId, upload) } returns storedFile
        every {
            resumeRegistrar.register(memberId, newResume)
        } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        assertThatThrownBy { resumeService.register(memberId, upload) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
        verify(exactly = 0) { resumeSummarizer.summarize(any()) }
    }

    private fun resumeUpload(): ResumeUpload {
        return ResumeUpload(
            originalName = "resume.pdf",
            contentType = "application/pdf",
            content = "pdf-content".toByteArray(),
        )
    }

    private fun resumeFile(memberId: UUID, upload: ResumeUpload): ResumeFile {
        return ResumeFile(
            key = "resumes/$memberId/resume.pdf",
            originalName = upload.originalName,
            sizeBytes = upload.content.size.toLong(),
            contentType = upload.contentType,
        )
    }
}
