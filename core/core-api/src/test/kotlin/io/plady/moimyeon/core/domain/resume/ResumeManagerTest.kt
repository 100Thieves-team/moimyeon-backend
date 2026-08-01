package io.plady.moimyeon.core.domain.resume

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
import java.time.LocalDateTime
import java.util.UUID

class ResumeManagerTest {
    private val memberRepository = mockk<MemberRepository>()
    private val resumeRepository = mockk<ResumeRepository>()
    private val resumeManager = ResumeManager(memberRepository, resumeRepository)

    private val memberId = UUID.randomUUID()

    @Test
    fun `기본 이력서를 변경하면 기존 기본을 해제하고 선택한 이력서를 기본으로 지정한다`() {
        val current = resumeEntity("기존 기본", isDefault = true)
        val selected = resumeEntity("새 기본", isDefault = false)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberIdAndArchivedAtIsNullAndDeletedAtIsNull(selected.id, memberId)
        } returns selected
        every {
            resumeRepository.findByMemberIdAndIsDefaultTrueAndArchivedAtIsNullAndDeletedAtIsNull(memberId)
        } returns current
        every { resumeRepository.flush() } just Runs

        resumeManager.makeDefault(memberId, selected.id)

        assertThat(current.isDefault).isFalse()
        assertThat(selected.isDefault).isTrue()
    }

    @Test
    fun `기본 이력서를 숨기려고 하면 E1012 를 던진다`() {
        val defaultResume = resumeEntity("기본", isDefault = true)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberIdAndArchivedAtIsNullAndDeletedAtIsNull(defaultResume.id, memberId)
        } returns defaultResume

        assertThatThrownBy { resumeManager.hide(memberId, defaultResume.id, LocalDateTime.of(2026, 8, 2, 12, 0)) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.DEFAULT_RESUME_CANNOT_BE_HIDDEN)
            }
    }

    @Test
    fun `기본이 아닌 이력서를 보관함 목록에서 숨긴다`() {
        val resume = resumeEntity("숨길 이력서", isDefault = false)
        val hiddenAt = LocalDateTime.of(2026, 8, 2, 12, 0)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberIdAndArchivedAtIsNullAndDeletedAtIsNull(resume.id, memberId)
        } returns resume

        resumeManager.hide(memberId, resume.id, hiddenAt)

        assertThat(resume.archivedAt).isEqualTo(hiddenAt)
    }

    private fun resumeEntity(name: String, isDefault: Boolean): ResumeEntity {
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
