package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class ResumeManager(
    private val memberRepository: MemberRepository,
    private val resumeRepository: ResumeRepository,
) {
    @Transactional
    fun makeDefault(memberId: UUID, resumeId: UUID) {
        lockMember(memberId)
        val selected = getSelectableResume(memberId, resumeId)
        if (selected.summaryStatus != ResumeSummaryStatus.DONE) {
            throw CoreException(CoreErrorType.RESUME_NOT_READY)
        }
        if (selected.isDefault) return

        val current = resumeRepository.findByMemberIdAndIsDefaultTrueAndDeletedAtIsNull(memberId)
        if (current != null) {
            current.releaseDefault()
            // 회원별 기본 이력서 유니크를 해제한 뒤 새 기본을 지정한다.
            resumeRepository.flush()
        }
        selected.makeDefault()
    }

    @Transactional
    fun completeSummary(memberId: UUID, resumeId: UUID, summary: String, completedAt: LocalDateTime) {
        lockMember(memberId)
        val resume = getSelectableResume(memberId, resumeId)
        if (!completedAt.isBefore(resume.summaryStartedAt.plusMinutes(SUMMARY_PROCESSING_TIMEOUT_MINUTES))) {
            resume.failSummary()
            return
        }
        resume.completeSummary(summary)

        if (resumeRepository.findByMemberIdAndIsDefaultTrueAndDeletedAtIsNull(memberId) == null) {
            resume.makeDefault()
        }
    }

    @Transactional
    fun failSummary(memberId: UUID, resumeId: UUID) {
        val resume = getSelectableResume(memberId, resumeId)
        resume.failSummary()
    }

    @Transactional
    fun startSummaryRetry(memberId: UUID, resumeId: UUID, startedAt: LocalDateTime) {
        lockMember(memberId)
        val resume = getSelectableResume(memberId, resumeId)
        if (resume.summaryStatus != ResumeSummaryStatus.FAILED) {
            throw CoreException(CoreErrorType.RESUME_SUMMARY_NOT_RETRYABLE)
        }
        resume.retrySummary(startedAt)
    }

    @Transactional
    fun failExpiredSummaries(memberId: UUID, now: LocalDateTime): Int {
        val expiredSummaries = resumeRepository
            .findByMemberIdAndSummaryStatusAndSummaryStartedAtLessThanEqualAndDeletedAtIsNull(
                memberId = memberId,
                summaryStatus = ResumeSummaryStatus.PROCESSING,
                startedAt = now.minusMinutes(SUMMARY_PROCESSING_TIMEOUT_MINUTES),
            )
        expiredSummaries.forEach { it.failSummary() }
        return expiredSummaries.size
    }

    @Transactional
    fun rename(memberId: UUID, resumeId: UUID, name: String) {
        val resume = getSelectableResume(memberId, resumeId)
        resume.rename(name)
    }

    @Transactional
    fun delete(memberId: UUID, resumeId: UUID, deletedAt: LocalDateTime) {
        lockMember(memberId)
        val resume = requireFound(
            resumeRepository.findByIdAndMemberId(resumeId, memberId),
            CoreErrorType.RESUME_NOT_FOUND,
        )
        if (resume.isDeleted()) return

        val replacement = if (resume.isDefault) {
            resumeRepository.findFirstByMemberIdAndIdNotAndSummaryStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                memberId,
                resumeId,
                ResumeSummaryStatus.DONE,
            )
        } else {
            null
        }
        resume.delete(deletedAt)

        if (replacement != null) {
            // 삭제한 기본 이력서가 점유하던 유니크 키를 먼저 해제한다.
            resumeRepository.flush()
            replacement.makeDefault()
        }
    }

    private fun lockMember(memberId: UUID) {
        // 보관함 행을 따로 두지 않으므로 회원 행이 같은 회원의 등록·기본 변경·삭제를 직렬화한다.
        val member = memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId)
        if (member == null) throw CoreException(CoreErrorType.MEMBER_NOT_FOUND)
    }

    private fun getSelectableResume(memberId: UUID, resumeId: UUID) = requireFound(
        resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resumeId, memberId),
        CoreErrorType.RESUME_NOT_FOUND,
    )
}

private const val SUMMARY_PROCESSING_TIMEOUT_MINUTES = 1L
