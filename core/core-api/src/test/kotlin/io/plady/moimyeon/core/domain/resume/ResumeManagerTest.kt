package io.plady.moimyeon.core.domain.resume

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(selected.id, memberId)
        } returns selected
        every {
            resumeRepository.findByMemberIdAndIsDefaultTrueAndDeletedAtIsNull(memberId)
        } returns current
        every { resumeRepository.flush() } just Runs

        resumeManager.makeDefault(memberId, selected.id)

        assertThat(current.isDefault).isFalse()
        assertThat(selected.isDefault).isTrue()
    }

    @Test
    fun `이미 기본인 이력서를 다시 지정하면 아무것도 변경하지 않는다`() {
        val selected = resumeEntity("현재 기본", isDefault = true)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(selected.id, memberId)
        } returns selected

        resumeManager.makeDefault(memberId, selected.id)

        assertThat(selected.isDefault).isTrue()
        verify(exactly = 0) {
            resumeRepository.findByMemberIdAndIsDefaultTrueAndDeletedAtIsNull(any())
        }
        verify(exactly = 0) { resumeRepository.flush() }
    }

    @Test
    fun `존재하지 않는 회원은 기본 이력서를 변경할 수 없다`() {
        val resumeId = UUID.randomUUID()
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns null

        assertThatThrownBy { resumeManager.makeDefault(memberId, resumeId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
        verify(exactly = 0) {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(any(), any())
        }
    }

    @Test
    fun `본인의 선택 가능한 이력서가 아니면 기본으로 지정할 수 없다`() {
        val resumeId = UUID.randomUUID()
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resumeId, memberId)
        } returns null

        assertThatThrownBy { resumeManager.makeDefault(memberId, resumeId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_NOT_FOUND)
            }
        verify(exactly = 0) {
            resumeRepository.findByMemberIdAndIsDefaultTrueAndDeletedAtIsNull(any())
        }
    }

    @Test
    fun `기본 이력서를 삭제하면 남은 이력서 중 최신 이력서를 기본으로 지정한다`() {
        val defaultResume = resumeEntity("기본", isDefault = true)
        val latestResume = resumeEntity("최신", isDefault = false)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberId(defaultResume.id, memberId)
        } returns defaultResume
        every {
            resumeRepository.findFirstByMemberIdAndIdNotAndDeletedAtIsNullOrderByCreatedAtDesc(memberId, defaultResume.id)
        } returns latestResume
        every { resumeRepository.flush() } just Runs

        resumeManager.delete(memberId, defaultResume.id, LocalDateTime.of(2026, 8, 2, 12, 0))

        assertThat(defaultResume.isDeleted()).isTrue()
        assertThat(latestResume.isDefault).isTrue()
        verify(exactly = 1) { resumeRepository.flush() }
    }

    @Test
    fun `유일한 기본 이력서는 삭제할 수 있다`() {
        val defaultResume = resumeEntity("유일한 기본", isDefault = true)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberId(defaultResume.id, memberId)
        } returns defaultResume
        every {
            resumeRepository.findFirstByMemberIdAndIdNotAndDeletedAtIsNullOrderByCreatedAtDesc(memberId, defaultResume.id)
        } returns null

        resumeManager.delete(memberId, defaultResume.id, LocalDateTime.of(2026, 8, 2, 12, 0))

        assertThat(defaultResume.isDeleted()).isTrue()
    }

    @Test
    fun `기본이 아닌 이력서를 삭제한다`() {
        val resume = resumeEntity("삭제할 이력서", isDefault = false)
        val deletedAt = LocalDateTime.of(2026, 8, 2, 12, 0)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberId(resume.id, memberId)
        } returns resume

        resumeManager.delete(memberId, resume.id, deletedAt)

        assertThat(resume.isDeleted()).isTrue()
    }

    @Test
    fun `이미 삭제한 이력서를 다시 삭제해도 성공한다`() {
        val resume = resumeEntity("이미 삭제한 이력서", isDefault = false)
        val firstDeletedAt = LocalDateTime.of(2026, 8, 2, 12, 0)
        val retriedAt = firstDeletedAt.plusHours(1)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberId(resume.id, memberId)
        } returns resume

        resumeManager.delete(memberId, resume.id, firstDeletedAt)
        resumeManager.delete(memberId, resume.id, retriedAt)

        assertThat(resume.isDeleted()).isTrue()
    }

    @Test
    fun `이력서 내용은 유지하고 이름만 변경한다`() {
        val resume = resumeEntity(
            name = "백엔드 지원용",
            isDefault = true,
            summaryStatus = ResumeSummaryStatus.DONE,
            summaryContent = "백엔드 개발 경력 3년",
        )
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resume.id, memberId)
        } returns resume

        resumeManager.rename(memberId, resume.id, "데이터 엔지니어 지원용")

        assertThat(resume.name).isEqualTo("데이터 엔지니어 지원용")
        assertThat(resume.originalName).isEqualTo("백엔드 지원용.pdf")
        assertThat(resume.fileKey).isEqualTo("resumes/$memberId/백엔드 지원용.pdf")
        assertThat(resume.summaryStatus).isEqualTo(ResumeSummaryStatus.DONE)
        assertThat(resume.summaryContent).isEqualTo("백엔드 개발 경력 3년")
    }

    @Test
    fun `본인의 선택 가능한 이력서가 아니면 이름을 변경할 수 없다`() {
        val resumeId = UUID.randomUUID()
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resumeId, memberId)
        } returns null

        assertThatThrownBy { resumeManager.rename(memberId, resumeId, "새 이름") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_NOT_FOUND)
            }
    }

    private fun resumeEntity(
        name: String,
        isDefault: Boolean,
        summaryStatus: ResumeSummaryStatus = ResumeSummaryStatus.PROCESSING,
        summaryContent: String? = null,
    ): ResumeEntity {
        return ResumeEntity(
            id = UUID.randomUUID(),
            memberId = memberId,
            name = name,
            fileKey = "resumes/$memberId/$name.pdf",
            originalName = "$name.pdf",
            sizeBytes = 1_024,
            contentType = "application/pdf",
            summaryStatus = summaryStatus,
            summaryContent = summaryContent,
            isDefault = isDefault,
        )
    }
}
