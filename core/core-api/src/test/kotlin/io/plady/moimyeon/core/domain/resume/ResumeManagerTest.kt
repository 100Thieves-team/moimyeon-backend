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
    fun `AI 요약을 완료하면 요약문을 저장하고 기본 이력서가 없을 때 기본으로 지정한다`() {
        val startedAt = LocalDateTime.of(2026, 8, 3, 12, 0)
        val resume = resumeEntity("첫 이력서", isDefault = false, summaryStartedAt = startedAt)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resume.id, memberId)
        } returns resume
        every {
            resumeRepository.findByMemberIdAndIsDefaultTrueAndDeletedAtIsNull(memberId)
        } returns null

        resumeManager.completeSummary(memberId, resume.id, "Kotlin Spring 백엔드 개발자", startedAt.plusSeconds(59))

        assertThat(resume.summaryStatus).isEqualTo(ResumeSummaryStatus.DONE)
        assertThat(resume.summaryContent).isEqualTo("Kotlin Spring 백엔드 개발자")
        assertThat(resume.isDefault).isTrue()
    }

    @Test
    fun `기본 이력서가 이미 있으면 AI 요약을 완료해도 기본을 바꾸지 않는다`() {
        val currentDefault = resumeEntity("기존 기본", isDefault = true, summaryStatus = ResumeSummaryStatus.DONE)
        val startedAt = LocalDateTime.of(2026, 8, 3, 12, 0)
        val resume = resumeEntity("새 이력서", isDefault = false, summaryStartedAt = startedAt)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resume.id, memberId)
        } returns resume
        every {
            resumeRepository.findByMemberIdAndIsDefaultTrueAndDeletedAtIsNull(memberId)
        } returns currentDefault

        resumeManager.completeSummary(memberId, resume.id, "새 이력서 요약", startedAt.plusSeconds(59))

        assertThat(resume.summaryStatus).isEqualTo(ResumeSummaryStatus.DONE)
        assertThat(resume.isDefault).isFalse()
        assertThat(currentDefault.isDefault).isTrue()
    }

    @Test
    fun `AI 요약에 실패하면 요약문 없이 실패 상태로 변경한다`() {
        val resume = resumeEntity("요약 실패", isDefault = false)
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resume.id, memberId)
        } returns resume

        resumeManager.failSummary(memberId, resume.id)

        assertThat(resume.summaryStatus).isEqualTo(ResumeSummaryStatus.FAILED)
        assertThat(resume.summaryContent).isNull()
        assertThat(resume.isDefault).isFalse()
    }

    @Test
    fun `이미 실패로 확정된 AI 요약을 다시 실패 처리해도 상태를 유지한다`() {
        val resume = resumeEntity("이미 실패", isDefault = false, summaryStatus = ResumeSummaryStatus.FAILED)
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resume.id, memberId)
        } returns resume

        resumeManager.failSummary(memberId, resume.id)

        assertThat(resume.summaryStatus).isEqualTo(ResumeSummaryStatus.FAILED)
        assertThat(resume.summaryContent).isNull()
    }

    @Test
    fun `AI 요약이 시작 후 1분에 완료되면 완료하지 않고 실패로 확정한다`() {
        val startedAt = LocalDateTime.of(2026, 8, 3, 12, 0)
        val resume = resumeEntity("시간 초과", isDefault = false, summaryStartedAt = startedAt)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resume.id, memberId)
        } returns resume

        resumeManager.completeSummary(memberId, resume.id, "늦게 도착한 요약", startedAt.plusMinutes(1))

        assertThat(resume.summaryStatus).isEqualTo(ResumeSummaryStatus.FAILED)
        assertThat(resume.summaryContent).isNull()
        assertThat(resume.isDefault).isFalse()
        verify(exactly = 0) {
            resumeRepository.findByMemberIdAndIsDefaultTrueAndDeletedAtIsNull(any())
        }
    }

    @Test
    fun `조회 시점에 1분 지난 처리 중 요약을 실패로 확정한다`() {
        val now = LocalDateTime.of(2026, 8, 3, 12, 0)
        val first = resumeEntity("첫 번째 시간 초과", isDefault = false, summaryStartedAt = now.minusMinutes(2))
        val second = resumeEntity("두 번째 시간 초과", isDefault = false, summaryStartedAt = now.minusMinutes(1))
        every {
            resumeRepository.findByMemberIdAndSummaryStatusAndSummaryStartedAtLessThanEqualAndDeletedAtIsNull(
                memberId,
                ResumeSummaryStatus.PROCESSING,
                now.minusMinutes(1),
            )
        } returns listOf(first, second)

        val expiredCount = resumeManager.failExpiredSummaries(memberId, now)

        assertThat(expiredCount).isEqualTo(2)
        assertThat(first.summaryStatus).isEqualTo(ResumeSummaryStatus.FAILED)
        assertThat(second.summaryStatus).isEqualTo(ResumeSummaryStatus.FAILED)
    }

    @Test
    fun `실패한 AI 요약은 처리 중 상태로 바꾸고 재시도를 시작한다`() {
        val resume = resumeEntity("재시도", isDefault = false, summaryStatus = ResumeSummaryStatus.FAILED)
        val restartedAt = LocalDateTime.of(2026, 8, 3, 12, 0)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resume.id, memberId)
        } returns resume

        resumeManager.startSummaryRetry(memberId, resume.id, restartedAt)

        assertThat(resume.summaryStatus).isEqualTo(ResumeSummaryStatus.PROCESSING)
        assertThat(resume.summaryStartedAt).isEqualTo(restartedAt)
    }

    @Test
    fun `처리 중이거나 완료된 AI 요약은 재시도할 수 없다`() {
        listOf(ResumeSummaryStatus.PROCESSING, ResumeSummaryStatus.DONE).forEach { status ->
            val resume = resumeEntity("재시도 불가", isDefault = status == ResumeSummaryStatus.DONE, summaryStatus = status)
            every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
            every {
                resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resume.id, memberId)
            } returns resume

            assertThatThrownBy {
                resumeManager.startSummaryRetry(memberId, resume.id, LocalDateTime.of(2026, 8, 3, 12, 0))
            }
                .isInstanceOfSatisfying(CoreException::class.java) {
                    assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_SUMMARY_NOT_RETRYABLE)
                }
        }
    }

    @Test
    fun `기본 이력서를 변경하면 기존 기본을 해제하고 선택한 이력서를 기본으로 지정한다`() {
        val current = resumeEntity("기존 기본", isDefault = true, summaryStatus = ResumeSummaryStatus.DONE)
        val selected = resumeEntity("새 기본", isDefault = false, summaryStatus = ResumeSummaryStatus.DONE)
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
        val selected = resumeEntity("현재 기본", isDefault = true, summaryStatus = ResumeSummaryStatus.DONE)
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
    fun `AI 요약이 완료되지 않은 이력서는 기본으로 지정할 수 없다`() {
        val selected = resumeEntity("요약 중", isDefault = false)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(selected.id, memberId)
        } returns selected

        assertThatThrownBy { resumeManager.makeDefault(memberId, selected.id) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_NOT_READY)
            }
    }

    @Test
    fun `기본 이력서를 삭제하면 남은 이력서 중 최신 이력서를 기본으로 지정한다`() {
        val defaultResume = resumeEntity("기본", isDefault = true, summaryStatus = ResumeSummaryStatus.DONE)
        val latestResume = resumeEntity("최신", isDefault = false, summaryStatus = ResumeSummaryStatus.DONE)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberId(defaultResume.id, memberId)
        } returns defaultResume
        every {
            resumeRepository.findFirstByMemberIdAndIdNotAndSummaryStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                memberId,
                defaultResume.id,
                ResumeSummaryStatus.DONE,
            )
        } returns latestResume
        every { resumeRepository.flush() } just Runs

        resumeManager.delete(memberId, defaultResume.id, LocalDateTime.of(2026, 8, 2, 12, 0))

        assertThat(defaultResume.isDeleted()).isTrue()
        assertThat(latestResume.isDefault).isTrue()
        verify(exactly = 1) { resumeRepository.flush() }
    }

    @Test
    fun `유일한 기본 이력서는 삭제할 수 있다`() {
        val defaultResume = resumeEntity("유일한 기본", isDefault = true, summaryStatus = ResumeSummaryStatus.DONE)
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns mockk<MemberEntity>()
        every {
            resumeRepository.findByIdAndMemberId(defaultResume.id, memberId)
        } returns defaultResume
        every {
            resumeRepository.findFirstByMemberIdAndIdNotAndSummaryStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                memberId,
                defaultResume.id,
                ResumeSummaryStatus.DONE,
            )
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
        summaryStartedAt: LocalDateTime = LocalDateTime.of(2026, 8, 3, 12, 0),
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
            summaryStartedAt = summaryStartedAt,
            isDefault = isDefault,
        )
    }
}
