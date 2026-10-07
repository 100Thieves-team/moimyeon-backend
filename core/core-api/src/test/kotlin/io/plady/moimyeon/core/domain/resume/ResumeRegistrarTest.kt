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
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class ResumeRegistrarTest {
    private val memberRepository = mockk<MemberRepository>()
    private val resumeRepository = mockk<ResumeRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-08-03T12:00:00Z"), ZoneOffset.UTC)
    private val resumeRegistrar = ResumeRegistrar(memberRepository, resumeRepository, clock)

    private val memberId = UUID.randomUUID()
    private val file = ResumeFile(
        key = "resumes/$memberId/resume.pdf",
        originalName = "resume.pdf",
        sizeBytes = 1_024,
        contentType = "application/pdf",
    )
    private val newResume = NewResume(file.originalName, file)

    @Test
    fun `활성 이력서가 10개면 E1011 을 던진다`() {
        every { resumeRepository.countByMemberIdAndDeletedAtIsNull(memberId) } returns 10

        assertThatThrownBy { resumeRegistrar.validateCapacity(memberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_LIMIT_EXCEEDED)
            }
    }

    @Test
    fun `파일 저장 중 이력서가 10개가 되면 트랜잭션 안에서 등록을 거절한다`() {
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every { resumeRepository.countByMemberIdAndDeletedAtIsNull(memberId) } returns 10

        assertThatThrownBy { resumeRegistrar.register(memberId, newResume) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_LIMIT_EXCEEDED)
            }
        verify(exactly = 0) { resumeRepository.save(any()) }
    }

    @Test
    fun `첫 이력서도 기본으로 노출하지 않고 요약 처리 중 상태로 등록한다`() {
        val savedResume = slot<ResumeEntity>()
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every { resumeRepository.countByMemberIdAndDeletedAtIsNull(memberId) } returns 0
        every { resumeRepository.save(capture(savedResume)) } answers { savedResume.captured }

        val resumeId = resumeRegistrar.register(memberId, newResume)

        val entity = savedResume.captured
        assertThat(resumeId).isEqualTo(entity.id)
        assertThat(entity.memberId).isEqualTo(memberId)
        assertThat(entity.name).isEqualTo(file.originalName)
        assertThat(entity.fileKey).isEqualTo(file.key)
        assertThat(entity.originalName).isEqualTo(file.originalName)
        assertThat(entity.sizeBytes).isEqualTo(file.sizeBytes)
        assertThat(entity.contentType).isEqualTo(file.contentType)
        assertThat(entity.summaryStatus).isEqualTo(ResumeSummaryStatus.PROCESSING)
        assertThat(entity.summaryContent).isNull()
        assertThat(entity.summaryStartedAt).isEqualTo(LocalDateTime.of(2026, 8, 3, 12, 0))
        assertThat(entity.isDefault).isFalse()
    }

    @Test
    fun `두 번째 이력서는 기본 이력서로 등록하지 않는다`() {
        val savedResume = slot<ResumeEntity>()
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every { resumeRepository.countByMemberIdAndDeletedAtIsNull(memberId) } returns 1
        every { resumeRepository.save(capture(savedResume)) } answers { savedResume.captured }

        resumeRegistrar.register(memberId, newResume)

        assertThat(savedResume.captured.isDefault).isFalse()
        verify(exactly = 1) { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) }
    }
}
