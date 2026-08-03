package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.SocialAuthService
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ResumeEntity
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ResumeManagementIT(
    private val socialAuthService: SocialAuthService,
    private val resumeFinder: ResumeFinder,
    private val resumeManager: ResumeManager,
    private val resumeRegistrar: ResumeRegistrar,
    private val resumeRepository: ResumeRepository,
) : ContextTest() {
    @Test
    fun `처리를 시작한 지 1분 지난 요약만 실패로 확정한다`() {
        val memberId = signUp("resume-summary-expiration")
        val now = LocalDateTime.of(2026, 8, 3, 12, 0)
        val expired = saveProcessingResume(memberId, "expired.pdf", now.minusMinutes(1))
        val processing = saveProcessingResume(memberId, "processing.pdf", now.minusSeconds(59))

        val expiredCount = resumeManager.failExpiredSummaries(memberId, now)

        assertThat(expiredCount).isEqualTo(1)
        assertThat(resumeRepository.findById(expired.id).orElseThrow().summaryStatus)
            .isEqualTo(ResumeSummaryStatus.FAILED)
        assertThat(resumeRepository.findById(processing.id).orElseThrow().summaryStatus)
            .isEqualTo(ResumeSummaryStatus.PROCESSING)
    }

    @Test
    fun `보관 목록은 기본 이력서를 먼저 보여주고 나머지는 최신순으로 보여준다`() {
        val memberId = signUp("resume-management-order")
        val defaultResumeId = registerDone(memberId, "default.pdf")
        val olderResumeId = registerDone(memberId, "older.pdf")
        val newerResumeId = registerDone(memberId, "newer.pdf")

        val resumes = resumeFinder.getAll(memberId)

        assertThat(resumes).extracting("id").containsExactly(defaultResumeId, newerResumeId, olderResumeId)
    }

    @Test
    fun `기본 이력서를 바꾼 뒤 이전 이력서를 삭제하면 목록에는 새 기본만 남는다`() {
        val memberId = signUp("resume-management-1")
        val firstResumeId = registerDone(memberId, "first.pdf")
        val secondResumeId = registerDone(memberId, "second.pdf")

        resumeManager.makeDefault(memberId, secondResumeId)
        resumeManager.delete(memberId, firstResumeId, LocalDateTime.of(2026, 8, 2, 12, 0))

        val resumes = resumeFinder.getAll(memberId)
        assertThat(resumes).extracting("id").containsExactly(secondResumeId)
        assertThat(resumes.single().isDefault).isTrue()
        assertThat(resumeRepository.findById(firstResumeId).orElseThrow().isDeleted()).isTrue()
    }

    @Test
    fun `기본 이력서 변경은 기존 기본 해제와 새 기본 지정을 함께 커밋한다`() {
        val memberId = signUp("resume-management-default")
        val firstResumeId = registerDone(memberId, "first-default.pdf")
        val secondResumeId = registerDone(memberId, "second-default.pdf")

        resumeManager.makeDefault(memberId, secondResumeId)

        val first = resumeRepository.findById(firstResumeId).orElseThrow()
        val second = resumeRepository.findById(secondResumeId).orElseThrow()
        assertThat(first.isDefault).isFalse()
        assertThat(second.isDefault).isTrue()
    }

    @Test
    fun `기본 이력서를 삭제하면 남은 이력서 중 최신 이력서를 기본으로 지정한다`() {
        val memberId = signUp("resume-management-delete-default")
        val defaultResumeId = registerDone(memberId, "default.pdf")
        val olderResumeId = registerDone(memberId, "older.pdf")
        val latestResumeId = registerDone(memberId, "latest.pdf")

        resumeManager.delete(memberId, defaultResumeId, LocalDateTime.of(2026, 8, 2, 12, 0))

        val resumes = resumeFinder.getAll(memberId)
        assertThat(resumes).extracting("id").containsExactly(latestResumeId, olderResumeId)
        assertThat(resumes.first().isDefault).isTrue()
        assertThat(resumeRepository.findById(defaultResumeId).orElseThrow().isDeleted()).isTrue()
    }

    @Test
    fun `유일한 기본 이력서를 삭제하면 보관함이 비게 된다`() {
        val memberId = signUp("resume-management-delete-only")
        val resumeId = registerDone(memberId, "only.pdf")

        resumeManager.delete(memberId, resumeId, LocalDateTime.of(2026, 8, 2, 12, 0))

        assertThat(resumeFinder.getAll(memberId)).isEmpty()
        assertThat(resumeRepository.findById(resumeId).orElseThrow().isDeleted()).isTrue()
    }

    @Test
    fun `같은 이력서 삭제를 반복해도 삭제 상태를 유지한다`() {
        val memberId = signUp("resume-management-idempotent-delete")
        registerDone(memberId, "default-idempotent-delete.pdf")
        val resumeId = registerDone(memberId, "idempotent-delete.pdf")
        val firstDeletedAt = LocalDateTime.of(2026, 8, 3, 12, 0)

        resumeManager.delete(memberId, resumeId, firstDeletedAt)
        resumeManager.delete(memberId, resumeId, firstDeletedAt.plusHours(1))

        assertThat(resumeRepository.findById(resumeId).orElseThrow().isDeleted()).isTrue()
    }

    @Test
    fun `이력서 이름을 변경해도 원본 파일 정보는 유지한다`() {
        val memberId = signUp("resume-management-3")
        val resumeId = registerDone(memberId, "backend.pdf")

        resumeManager.rename(memberId, resumeId, "데이터 엔지니어 지원용")

        val resume = resumeFinder.getAll(memberId).single()
        assertThat(resume.name).isEqualTo("데이터 엔지니어 지원용")
        assertThat(resume.file.originalName).isEqualTo("backend.pdf")
    }

    @Test
    fun `삭제한 이력서는 이름을 변경할 수 없다`() {
        val memberId = signUp("resume-management-deleted-rename")
        registerDone(memberId, "default-deleted-rename.pdf")
        val deletedResumeId = registerDone(memberId, "deleted-rename.pdf")
        resumeManager.delete(memberId, deletedResumeId, LocalDateTime.of(2026, 8, 3, 12, 0))

        assertThatThrownBy {
            resumeManager.rename(memberId, deletedResumeId, "변경할 수 없는 이름")
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_NOT_FOUND)
        }
        assertThat(resumeRepository.findById(deletedResumeId).orElseThrow().name).isEqualTo("deleted-rename.pdf")
    }

    private fun signUp(providerId: String): UUID {
        return socialAuthService.authenticate(
            SocialLoginProvider.GOOGLE,
            providerId,
            Email("$providerId@example.com"),
        )
    }

    private fun newResume(memberId: UUID, originalName: String): NewResume {
        return NewResume(
            name = originalName,
            file = ResumeFile(
                key = "resumes/$memberId/$originalName",
                originalName = originalName,
                sizeBytes = 1_024,
                contentType = "application/pdf",
            ),
        )
    }

    private fun registerDone(memberId: UUID, originalName: String): UUID {
        val resumeId = resumeRegistrar.register(memberId, newResume(memberId, originalName))
        val startedAt = resumeRepository.findById(resumeId).orElseThrow().summaryStartedAt
        resumeManager.completeSummary(memberId, resumeId, "$originalName 요약", startedAt, startedAt.plusSeconds(1))
        return resumeId
    }

    private fun saveProcessingResume(
        memberId: UUID,
        originalName: String,
        summaryStartedAt: LocalDateTime,
    ): ResumeEntity {
        return resumeRepository.save(
            ResumeEntity(
                id = UUID.randomUUID(),
                memberId = memberId,
                name = originalName,
                fileKey = "resumes/$memberId/$originalName",
                originalName = originalName,
                sizeBytes = 1_024,
                contentType = "application/pdf",
                summaryStatus = ResumeSummaryStatus.PROCESSING,
                summaryStartedAt = summaryStartedAt,
                isDefault = false,
            ),
        )
    }
}
