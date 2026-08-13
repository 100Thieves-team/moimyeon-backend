package io.plady.moimyeon.core.domain.resume

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class ResumeServiceTest {
    private val resumeFinder = mockk<ResumeFinder>()
    private val storedResumeReader = mockk<StoredResumeReader>()
    private val fileStorage = mockk<ResumeFileStore>()
    private val resumeManager = mockk<ResumeManager>()
    private val resumeRegistrar = mockk<ResumeRegistrar>()
    private val summaryGenerator = mockk<ResumeSummaryGenerator>()
    private val now = LocalDateTime.of(2026, 8, 3, 12, 0)
    private val clock = Clock.fixed(Instant.parse("2026-08-03T12:00:00Z"), ZoneOffset.UTC)
    private val resumeService =
        ResumeService(
            resumeFinder,
            storedResumeReader,
            fileStorage,
            resumeManager,
            resumeRegistrar,
            summaryGenerator,
            clock,
        )

    @Test
    fun `회원의 저장 이력서 목록은 만료된 요약을 정리한 뒤 최근 사용 정보와 함께 조회한다`() {
        val memberId = UUID.randomUUID()
        val storedResumes = listOf(mockk<StoredResume>())
        every { resumeManager.failExpiredSummaries(memberId, now) } returns 0
        every { storedResumeReader.getAll(memberId) } returns storedResumes

        val result = resumeService.getStored(memberId)

        assertThat(result).isSameAs(storedResumes)
        verifyOrder {
            resumeManager.failExpiredSummaries(memberId, now)
            storedResumeReader.getAll(memberId)
        }
    }

    @Test
    fun `회원의 이력서 목록을 조회한다`() {
        val memberId = UUID.randomUUID()
        val resumes = listOf(mockk<Resume>())
        every { resumeManager.failExpiredSummaries(memberId, now) } returns 0
        every { resumeFinder.getAll(memberId) } returns resumes

        val result = resumeService.getAll(memberId)

        assertThat(result).isSameAs(resumes)
        verifyOrder {
            resumeManager.failExpiredSummaries(memberId, now)
            resumeFinder.getAll(memberId)
        }
        verify(exactly = 0) { summaryGenerator.generate(any()) }
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
        every { resumeManager.delete(memberId, resumeId, now) } just Runs

        resumeService.delete(memberId, resumeId)

        verify(exactly = 1) { resumeManager.delete(memberId, resumeId, now) }
    }

    @Test
    fun `PDF를 등록하면 원본을 보관하고 AI 요약까지 완료한다`() {
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
        every { resumeRegistrar.register(memberId, newResume, now) } returns resumeId
        every { summaryGenerator.generate(upload.content) } returns "Kotlin Spring 백엔드 개발자"
        every {
            resumeManager.completeSummary(memberId, resumeId, "Kotlin Spring 백엔드 개발자", now, now)
        } just Runs

        val registeredResumeId = resumeService.register(memberId, upload)

        assertThat(registeredResumeId).isEqualTo(resumeId)
        verifyOrder {
            resumeRegistrar.validateCapacity(memberId)
            fileStorage.store(memberId, upload)
            resumeRegistrar.register(memberId, newResume, now)
            summaryGenerator.generate(upload.content)
            resumeManager.completeSummary(memberId, resumeId, "Kotlin Spring 백엔드 개발자", now, now)
        }
    }

    @Test
    fun `AI 요약에 실패해도 이력서 등록은 성공하고 실패 상태로 남긴다`() {
        val memberId = UUID.randomUUID()
        val resumeId = UUID.randomUUID()
        val upload = resumeUpload()
        val storedFile = resumeFile(memberId, upload)
        val newResume = NewResume(storedFile.originalName, storedFile)
        every { resumeRegistrar.validateCapacity(memberId) } just Runs
        every { fileStorage.store(memberId, upload) } returns storedFile
        every { resumeRegistrar.register(memberId, newResume, now) } returns resumeId
        every {
            summaryGenerator.generate(upload.content)
        } throws ResumeSummaryGenerationException(IllegalStateException("bedrock unavailable"))
        every { resumeManager.failSummary(memberId, resumeId, now) } just Runs

        val registeredResumeId = resumeService.register(memberId, upload)

        assertThat(registeredResumeId).isEqualTo(resumeId)
        verifyOrder {
            resumeRegistrar.validateCapacity(memberId)
            fileStorage.store(memberId, upload)
            resumeRegistrar.register(memberId, newResume, now)
            summaryGenerator.generate(upload.content)
            resumeManager.failSummary(memberId, resumeId, now)
        }
        verify(exactly = 0) { resumeManager.completeSummary(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `실패한 AI 요약을 재시도하면 S3 원본으로 요약을 다시 완료한다`() {
        val memberId = UUID.randomUUID()
        val resumeId = UUID.randomUUID()
        val file = resumeFile(memberId, resumeUpload())
        val content = "stored-pdf-content".toByteArray()
        every { resumeManager.failExpiredSummaries(memberId, now) } returns 1
        every { resumeFinder.get(memberId, resumeId) } returns resume(resumeId, file)
        every { resumeManager.startSummaryRetry(memberId, resumeId, now) } just Runs
        every { fileStorage.read(file) } returns content
        every { summaryGenerator.generate(content) } returns "재생성한 요약"
        every { resumeManager.completeSummary(memberId, resumeId, "재생성한 요약", now, now) } just Runs

        val retriedResumeId = resumeService.retrySummary(memberId, resumeId)

        assertThat(retriedResumeId).isEqualTo(resumeId)
        verifyOrder {
            resumeManager.failExpiredSummaries(memberId, now)
            resumeFinder.get(memberId, resumeId)
            resumeManager.startSummaryRetry(memberId, resumeId, now)
            fileStorage.read(file)
            summaryGenerator.generate(content)
            resumeManager.completeSummary(memberId, resumeId, "재생성한 요약", now, now)
        }
    }

    @Test
    fun `재시도 중 AI 요약이 다시 실패하면 실패 상태로 되돌린다`() {
        val memberId = UUID.randomUUID()
        val resumeId = UUID.randomUUID()
        val file = resumeFile(memberId, resumeUpload())
        val content = "stored-pdf-content".toByteArray()
        every { resumeManager.failExpiredSummaries(memberId, now) } returns 0
        every { resumeFinder.get(memberId, resumeId) } returns resume(resumeId, file)
        every { resumeManager.startSummaryRetry(memberId, resumeId, now) } just Runs
        every { fileStorage.read(file) } returns content
        every {
            summaryGenerator.generate(content)
        } throws ResumeSummaryGenerationException(IllegalStateException("bedrock unavailable"))
        every { resumeManager.failSummary(memberId, resumeId, now) } just Runs

        val retriedResumeId = resumeService.retrySummary(memberId, resumeId)

        assertThat(retriedResumeId).isEqualTo(resumeId)
        verify(exactly = 1) { resumeManager.failSummary(memberId, resumeId, now) }
        verify(exactly = 0) { resumeManager.completeSummary(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `예상하지 않은 요약 구현 오류는 실패 상태로 숨기지 않는다`() {
        val memberId = UUID.randomUUID()
        val resumeId = UUID.randomUUID()
        val upload = resumeUpload()
        val storedFile = resumeFile(memberId, upload)
        val newResume = NewResume(storedFile.originalName, storedFile)
        every { resumeRegistrar.validateCapacity(memberId) } just Runs
        every { fileStorage.store(memberId, upload) } returns storedFile
        every { resumeRegistrar.register(memberId, newResume, now) } returns resumeId
        every { summaryGenerator.generate(upload.content) } throws IllegalStateException("implementation bug")

        assertThatThrownBy { resumeService.register(memberId, upload) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("implementation bug")
        verify(exactly = 0) { resumeManager.failSummary(any(), any(), any()) }
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
        verify(exactly = 0) { resumeRegistrar.register(any(), any(), any()) }
        verify(exactly = 0) { summaryGenerator.generate(any()) }
    }

    @Test
    fun `원본 파일 저장에 실패하면 이력서를 등록하거나 AI 요약을 시작하지 않는다`() {
        val memberId = UUID.randomUUID()
        val upload = resumeUpload()
        every { resumeRegistrar.validateCapacity(memberId) } just Runs
        every { fileStorage.store(memberId, upload) } throws IllegalStateException("file storage unavailable")

        assertThatThrownBy { resumeService.register(memberId, upload) }
            .isInstanceOf(IllegalStateException::class.java)
        verify(exactly = 0) { resumeRegistrar.register(any(), any(), any()) }
        verify(exactly = 0) { summaryGenerator.generate(any()) }
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
            resumeRegistrar.register(memberId, newResume, now)
        } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        assertThatThrownBy { resumeService.register(memberId, upload) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
        verify(exactly = 0) { summaryGenerator.generate(any()) }
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

    private fun resume(resumeId: UUID, file: ResumeFile): Resume {
        return Resume(
            id = resumeId,
            name = file.originalName,
            file = file,
            summary = ResumeSummary(ResumeSummaryStatus.FAILED, null),
            isDefault = false,
            registeredAt = now,
        )
    }
}
