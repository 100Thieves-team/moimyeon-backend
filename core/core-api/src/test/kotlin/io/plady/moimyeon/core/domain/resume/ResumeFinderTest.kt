package io.plady.moimyeon.core.domain.resume

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.storage.db.core.ResumeEntity
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ResumeFinderTest {
    private val resumeRepository = mockk<ResumeRepository>()
    private val resumeFinder = ResumeFinder(resumeRepository)

    @Test
    fun `선택 가능한 이력서를 모아 회원의 보관함으로 조립한다`() {
        val memberId = UUID.randomUUID()
        val defaultResume = resumeEntity(memberId, "백엔드 지원용", isDefault = true)
        val otherResume = resumeEntity(memberId, "커머스 지원용", isDefault = false)
        every {
            resumeRepository.findByMemberIdAndArchivedAtIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(memberId)
        } returns listOf(defaultResume, otherResume)

        val vault = resumeFinder.getVault(memberId)

        assertThat(vault.maxCount).isEqualTo(10)
        assertThat(vault.resumes).extracting("id").containsExactly(defaultResume.id, otherResume.id)
        assertThat(vault.defaultResume?.id).isEqualTo(defaultResume.id)
    }

    private fun resumeEntity(memberId: UUID, name: String, isDefault: Boolean): ResumeEntity {
        return ResumeEntity(
            id = UUID.randomUUID(),
            memberId = memberId,
            name = name,
            fileKey = "resumes/$memberId/$name.pdf",
            originalName = "$name.pdf",
            sizeBytes = 1_024,
            contentType = "application/pdf",
            summaryStatus = ResumeSummaryStatus.PROCESSING,
            isDefault = isDefault,
        )
    }
}
