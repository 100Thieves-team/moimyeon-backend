package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ResumeVaultTest {
    @Test
    fun `이력서가 있는 보관함에는 기본 이력서가 하나 있어야 한다`() {
        assertThatThrownBy { ResumeVault(listOf(resume(isDefault = false))) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `보관함에는 이력서를 10개보다 많이 담을 수 없다`() {
        val resumes = (1..11).map { resume(isDefault = it == 1) }

        assertThatThrownBy { ResumeVault(resumes) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun resume(isDefault: Boolean): Resume {
        return Resume(
            id = UUID.randomUUID(),
            name = "이력서",
            file = ResumeFile(
                key = "resumes/resume.pdf",
                originalName = "resume.pdf",
                sizeBytes = 1_024,
                contentType = "application/pdf",
            ),
            summary = ResumeSummary(ResumeSummaryStatus.PROCESSING, null),
            isDefault = isDefault,
            registeredAt = LocalDateTime.of(2026, 8, 2, 12, 0),
        )
    }
}
