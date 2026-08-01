package io.plady.moimyeon.core.domain.resume

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ResumeEntity
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ResumeRegistrarTest {
    private val memberRepository = mockk<MemberRepository>()
    private val resumeRepository = mockk<ResumeRepository>()
    private val resumeRegistrar = ResumeRegistrar(memberRepository, resumeRepository)

    private val memberId = UUID.randomUUID()
    private val file = ResumeFile(
        key = "resumes/$memberId/resume.pdf",
        originalName = "resume.pdf",
        sizeBytes = 1_024,
        contentType = "application/pdf",
    )

    @Test
    fun `활성 이력서가 10개면 E1011 을 던진다`() {
        every { resumeRepository.countByMemberIdAndArchivedAtIsNullAndDeletedAtIsNull(memberId) } returns 10

        assertThatThrownBy { resumeRegistrar.validateCapacity(memberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_LIMIT_EXCEEDED)
            }
    }

    @Test
    fun `첫 이력서를 기본 이력서이자 요약 처리 중 상태로 등록한다`() {
        val savedResume = slot<ResumeEntity>()
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every { resumeRepository.countByMemberIdAndArchivedAtIsNullAndDeletedAtIsNull(memberId) } returns 0
        every { resumeRepository.save(capture(savedResume)) } answers { savedResume.captured }

        val resumeId = resumeRegistrar.register(memberId, "백엔드 지원용", file)

        val entity = savedResume.captured
        assertThat(resumeId).isEqualTo(entity.id)
        assertThat(entity.memberId).isEqualTo(memberId)
        assertThat(entity.name).isEqualTo("백엔드 지원용")
        assertThat(entity.fileKey).isEqualTo(file.key)
        assertThat(entity.originalName).isEqualTo(file.originalName)
        assertThat(entity.sizeBytes).isEqualTo(file.sizeBytes)
        assertThat(entity.contentType).isEqualTo(file.contentType)
        assertThat(entity.summaryStatus).isEqualTo(ResumeSummaryStatus.PROCESSING)
        assertThat(entity.summaryContent).isNull()
        assertThat(entity.isDefault).isTrue()
    }

    @Test
    fun `두 번째 이력서는 기본 이력서로 등록하지 않는다`() {
        val savedResume = slot<ResumeEntity>()
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every { resumeRepository.countByMemberIdAndArchivedAtIsNullAndDeletedAtIsNull(memberId) } returns 1
        every { resumeRepository.save(capture(savedResume)) } answers { savedResume.captured }

        resumeRegistrar.register(memberId, "커머스 지원용", file)

        assertThat(savedResume.captured.isDefault).isFalse()
        verify(exactly = 1) { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) }
    }
}
