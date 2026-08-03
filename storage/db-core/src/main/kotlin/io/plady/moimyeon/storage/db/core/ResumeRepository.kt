package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface ResumeRepository : JpaRepository<ResumeEntity, UUID> {
    fun countByMemberIdAndDeletedAtIsNull(memberId: UUID): Long

    fun findByMemberIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(
        memberId: UUID,
    ): List<ResumeEntity>

    fun findByIdAndMemberIdAndDeletedAtIsNull(resumeId: UUID, memberId: UUID): ResumeEntity?

    fun findByIdAndMemberId(resumeId: UUID, memberId: UUID): ResumeEntity?

    fun findByMemberIdAndIsDefaultTrueAndDeletedAtIsNull(memberId: UUID): ResumeEntity?

    fun findFirstByMemberIdAndIdNotAndSummaryStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
        memberId: UUID,
        excludedResumeId: UUID,
        summaryStatus: ResumeSummaryStatus,
    ): ResumeEntity?

    fun findByMemberIdAndSummaryStatusAndSummaryStartedAtLessThanEqualAndDeletedAtIsNull(
        memberId: UUID,
        summaryStatus: ResumeSummaryStatus,
        startedAt: LocalDateTime,
    ): List<ResumeEntity>
}
