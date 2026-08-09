package io.plady.moimyeon.core.domain.resume

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ResumeEntity
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
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

    @Test
    fun `보관함에서 삭제했어도 제출 기록이 참조하는 이력서 요약은 조회한다`() {
        val memberId = UUID.randomUUID()
        val resume = resumeEntity(memberId, "백엔드 지원용", isDefault = false)
        resume.completeSummary("백엔드 개발 경험이 있습니다.")
        resume.delete(LocalDateTime.of(2026, 8, 5, 12, 0))
        every { resumeRepository.findByIdAndMemberId(resume.id, memberId) } returns resume

        val summary = resumeFinder.getSummary(memberId, resume.id)

        assertThat(summary).isEqualTo(
            ResumeSummary(
                status = ResumeSummaryStatus.DONE,
                content = "백엔드 개발 경험이 있습니다.",
            ),
        )
        verify(exactly = 1) { resumeRepository.findByIdAndMemberId(resume.id, memberId) }
    }

    @Test
    fun `제출 기록이 참조한 이력서가 없으면 RESUME_NOT_FOUND를 던진다`() {
        val memberId = UUID.randomUUID()
        val resumeId = UUID.randomUUID()
        every { resumeRepository.findByIdAndMemberId(resumeId, memberId) } returns null

        assertThatThrownBy {
            resumeFinder.getSummary(memberId, resumeId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_NOT_FOUND)
        }
    }

    @Test
    fun `여러 제출 기록의 이력서 요약을 한 번에 조회한다`() {
        val memberId = UUID.randomUUID()
        val first = resumeEntity(memberId, "백엔드 지원용", isDefault = false)
        val second = resumeEntity(memberId, "데이터 지원용", isDefault = false)
        first.completeSummary("백엔드 개발 경험")
        second.completeSummary("데이터 파이프라인 경험")
        every { resumeRepository.findByIdIn(listOf(first.id, second.id)) } returns listOf(first, second)

        val summaries = resumeFinder.getSummaries(listOf(first.id, second.id))

        assertThat(summaries).containsEntry(
            first.id,
            ResumeSummary(ResumeSummaryStatus.DONE, "백엔드 개발 경험"),
        ).containsEntry(
            second.id,
            ResumeSummary(ResumeSummaryStatus.DONE, "데이터 파이프라인 경험"),
        )
        verify(exactly = 1) { resumeRepository.findByIdIn(listOf(first.id, second.id)) }
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
            summaryStartedAt = LocalDateTime.of(2026, 8, 3, 12, 0),
            isDefault = isDefault,
        )
    }
}
