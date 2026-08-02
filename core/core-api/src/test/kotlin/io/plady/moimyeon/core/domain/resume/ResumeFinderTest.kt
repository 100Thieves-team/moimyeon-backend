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
    fun `보관한 이력서가 없으면 빈 목록을 반환한다`() {
        val memberId = UUID.randomUUID()
        every {
            resumeRepository.findByMemberIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(
                memberId,
            )
        } returns emptyList()

        val resumes = resumeFinder.getAll(memberId)

        assertThat(resumes).isEmpty()
    }

    @Test
    fun `선택 가능한 회원의 이력서를 조회한다`() {
        val memberId = UUID.randomUUID()
        val defaultResume = resumeEntity(memberId, "백엔드 지원용", isDefault = true)
        val otherResume = resumeEntity(memberId, "커머스 지원용", isDefault = false)
        every {
            resumeRepository.findByMemberIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(
                memberId,
            )
        } returns listOf(defaultResume, otherResume)

        val resumes = resumeFinder.getAll(memberId)

        assertThat(resumes).extracting("id").containsExactly(defaultResume.id, otherResume.id)
        assertThat(resumes.first().isDefault).isTrue()
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
