package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ResumeEntity
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaResumeSummaryCompleter(
    private val resumeRepository: ResumeRepository,
    private val memberRepository: MemberRepository,
    private val clock: Clock,
) {
    // ResumeManager.completeSummary 는 시도 시각 일치·45초 타임아웃을 판정하므로 QA 강제에 못 쓴다. 남의 엔티티를 내 커밋에서 바꾼다.
    @Transactional
    fun complete(resumeId: UUID, summary: String): QaResumeSummary {
        log.debug { "qa-resume.completer.complete resumeId=$resumeId" }
        val resume = requireFound(
            resumeRepository.findById(resumeId).orElse(null)?.takeIf { it.isActive() },
            CoreErrorType.RESUME_NOT_FOUND,
        )
        requireBusiness(QaDataCondition.hasQaMarker(resume.name), CoreErrorType.QA_DATA_ONLY)
        requireFound(memberRepository.findForUpdateByIdAndDeletedAtIsNull(resume.memberId), CoreErrorType.MEMBER_NOT_FOUND)
        when (resume.summaryStatus) {
            ResumeSummaryStatus.FAILED -> {
                resume.retrySummary(LocalDateTime.now(clock))
                resume.completeSummary(summary)
            }
            ResumeSummaryStatus.PROCESSING -> resume.completeSummary(summary)
            ResumeSummaryStatus.DONE -> Unit
        }
        if (resumeRepository.findByMemberIdAndIsDefaultTrueAndDeletedAtIsNull(resume.memberId) == null) {
            resume.makeDefault()
        }
        return resume.toQaResumeSummary()
    }

    private fun ResumeEntity.toQaResumeSummary() = QaResumeSummary(
        resumeId = id,
        memberId = memberId,
        status = summaryStatus,
        content = summaryContent,
        isDefault = isDefault,
    )
}
