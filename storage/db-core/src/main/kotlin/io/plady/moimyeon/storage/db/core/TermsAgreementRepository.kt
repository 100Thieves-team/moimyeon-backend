package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

// append-only
interface TermsAgreementRepository : JpaRepository<TermsAgreementEntity, UUID> {
    fun existsByMemberIdAndTermsId(memberId: UUID, termsId: UUID): Boolean

    fun findByMemberId(memberId: UUID): List<TermsAgreementEntity>
}
