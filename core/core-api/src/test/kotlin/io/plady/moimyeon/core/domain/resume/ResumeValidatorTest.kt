package io.plady.moimyeon.core.domain.resume

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ResumeEntity
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ResumeValidatorTest {
    private val resumeRepository = mockk<ResumeRepository>()
    private val validator = ResumeValidator(resumeRepository)

    private val memberId = UUID.randomUUID()
    private val resumeId = UUID.randomUUID()

    @Test
    fun `회원이 보관 중인 이력서이면 제출할 원본 파일을 반환한다`() {
        val fileKey = "resumes/$memberId/backend.pdf"
        val entity = mockk<ResumeEntity>()
        every { entity.fileKey } returns fileKey
        every { entity.originalName } returns "backend.pdf"
        every { entity.sizeBytes } returns 1024L
        every { entity.contentType } returns "application/pdf"
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resumeId, memberId)
        } returns entity

        val file = validator.validateOwnedBy(memberId, resumeId)

        assertThat(file).isEqualTo(
            ResumeFile(
                key = fileKey,
                originalName = "backend.pdf",
                sizeBytes = 1024L,
                contentType = "application/pdf",
            ),
        )
    }

    @Test
    fun `회원이 보관 중인 이력서가 아니면 RESUME_NOT_FOUND 로 거부한다`() {
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resumeId, memberId)
        } returns null

        assertThatThrownBy {
            validator.validateOwnedBy(memberId, resumeId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_NOT_FOUND)
        }
    }
}
