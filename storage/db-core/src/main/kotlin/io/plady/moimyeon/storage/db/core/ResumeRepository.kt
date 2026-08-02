package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ResumeRepository : JpaRepository<ResumeEntity, UUID> {
    fun countByMemberIdAndDeletedAtIsNull(memberId: UUID): Long

    fun findByMemberIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(
        memberId: UUID,
    ): List<ResumeEntity>

    fun findByIdAndMemberIdAndDeletedAtIsNull(resumeId: UUID, memberId: UUID): ResumeEntity?

    fun findByIdAndMemberId(resumeId: UUID, memberId: UUID): ResumeEntity?

    fun findByMemberIdAndIsDefaultTrueAndDeletedAtIsNull(memberId: UUID): ResumeEntity?

    fun findFirstByMemberIdAndIdNotAndDeletedAtIsNullOrderByCreatedAtDesc(
        memberId: UUID,
        excludedResumeId: UUID,
    ): ResumeEntity?
}
