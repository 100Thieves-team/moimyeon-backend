package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.SocialAuthService
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ResumeVaultIT(
    private val socialAuthService: SocialAuthService,
    private val resumeFinder: ResumeFinder,
    private val resumeManager: ResumeManager,
    private val resumeRegistrar: ResumeRegistrar,
    private val resumeRepository: ResumeRepository,
) : ContextTest() {
    @Test
    fun `기본 이력서를 바꾼 뒤 이전 이력서를 숨기면 보관함에는 새 기본만 남는다`() {
        val memberId = signUp("resume-vault-1")
        val firstResumeId = resumeRegistrar.register(memberId, "첫 이력서", resumeFile(memberId, "first.pdf"))
        val secondResumeId = resumeRegistrar.register(memberId, "두 번째 이력서", resumeFile(memberId, "second.pdf"))

        resumeManager.makeDefault(memberId, secondResumeId)
        resumeManager.hide(memberId, firstResumeId, LocalDateTime.of(2026, 8, 2, 12, 0))

        val vault = resumeFinder.getVault(memberId)
        assertThat(vault.resumes).extracting("id").containsExactly(secondResumeId)
        assertThat(vault.defaultResume?.id).isEqualTo(secondResumeId)
        assertThat(resumeRepository.findById(firstResumeId).orElseThrow().archivedAt).isNotNull()
    }

    @Test
    fun `현재 기본 이력서는 숨길 수 없다`() {
        val memberId = signUp("resume-vault-2")
        val resumeId = resumeRegistrar.register(memberId, "기본 이력서", resumeFile(memberId, "default.pdf"))

        assertThatThrownBy {
            resumeManager.hide(memberId, resumeId, LocalDateTime.of(2026, 8, 2, 12, 0))
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.DEFAULT_RESUME_CANNOT_BE_HIDDEN)
        }
    }

    private fun signUp(providerId: String): UUID {
        return socialAuthService.authenticate(
            SocialLoginProvider.GOOGLE,
            providerId,
            Email("$providerId@example.com"),
        )
    }

    private fun resumeFile(memberId: UUID, originalName: String): ResumeFile {
        return ResumeFile(
            key = "resumes/$memberId/$originalName",
            originalName = originalName,
            sizeBytes = 1_024,
            contentType = "application/pdf",
        )
    }
}
