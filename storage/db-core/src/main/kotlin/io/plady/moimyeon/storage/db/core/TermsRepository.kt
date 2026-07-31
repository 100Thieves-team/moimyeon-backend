package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.TermsStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TermsRepository : JpaRepository<TermsEntity, UUID> {
    fun findByStatusAndDeletedAtIsNull(status: TermsStatus): List<TermsEntity>

    fun findByRequiredIsTrueAndStatusAndDeletedAtIsNull(status: TermsStatus): List<TermsEntity>
}
