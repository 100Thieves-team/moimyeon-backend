package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.TermsStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface TermsRepository : JpaRepository<TermsEntity, UUID> {
    fun findByStatusAndEffectiveFromLessThanEqualAndDeletedAtIsNull(
        status: TermsStatus,
        effectiveFrom: LocalDateTime,
    ): List<TermsEntity>

    fun findByIdAndStatusInAndEffectiveFromLessThanEqualAndDeletedAtIsNull(
        id: UUID,
        statuses: Collection<TermsStatus>,
        effectiveFrom: LocalDateTime,
    ): TermsEntity?

    fun findByStatusAndDeletedAtIsNull(status: TermsStatus): List<TermsEntity>

    fun findByRequiredIsTrueAndStatusAndDeletedAtIsNull(status: TermsStatus): List<TermsEntity>
}
