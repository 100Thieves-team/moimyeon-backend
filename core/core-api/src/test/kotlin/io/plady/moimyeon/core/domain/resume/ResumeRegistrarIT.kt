package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.SocialAuthService
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ResumeRegistrarIT(
    private val socialAuthService: SocialAuthService,
    private val resumeRegistrar: ResumeRegistrar,
    private val resumeRepository: ResumeRepository,
) : ContextTest() {
    @Test
    fun `첫 이력서는 기본이 되고 두 번째 이력서는 기본이 되지 않는다`() {
        val memberId = socialAuthService.authenticate(
            SocialLoginProvider.GOOGLE,
            "resume-registrar-1",
            Email("resume@example.com"),
        )

        val firstResumeId = resumeRegistrar.register(memberId, newResume(memberId, "first.pdf"))
        val secondResumeId = resumeRegistrar.register(memberId, newResume(memberId, "second.pdf"))

        val first = resumeRepository.findById(firstResumeId).orElseThrow()
        val second = resumeRepository.findById(secondResumeId).orElseThrow()
        assertThat(first.memberId).isEqualTo(memberId)
        assertThat(first.summaryStatus).isEqualTo(ResumeSummaryStatus.PROCESSING)
        assertThat(first.isDefault).isTrue()
        assertThat(second.memberId).isEqualTo(memberId)
        assertThat(second.summaryStatus).isEqualTo(ResumeSummaryStatus.PROCESSING)
        assertThat(second.isDefault).isFalse()
    }

    private fun resumeFile(memberId: UUID, originalName: String): ResumeFile {
        return ResumeFile(
            key = "resumes/$memberId/$originalName",
            originalName = originalName,
            sizeBytes = 1_024,
            contentType = "application/pdf",
        )
    }

    private fun newResume(memberId: UUID, originalName: String): NewResume {
        return NewResume(originalName, resumeFile(memberId, originalName))
    }
}
