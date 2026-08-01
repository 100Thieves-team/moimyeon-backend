package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ResumeRepository : JpaRepository<ResumeEntity, UUID> {
    fun countByMemberIdAndArchivedAtIsNullAndDeletedAtIsNull(memberId: UUID): Long

    fun findByMemberIdAndArchivedAtIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(memberId: UUID): List<ResumeEntity>

    fun findByIdAndMemberIdAndArchivedAtIsNullAndDeletedAtIsNull(resumeId: UUID, memberId: UUID): ResumeEntity?

    fun findByMemberIdAndIsDefaultTrueAndArchivedAtIsNullAndDeletedAtIsNull(memberId: UUID): ResumeEntity?
}
