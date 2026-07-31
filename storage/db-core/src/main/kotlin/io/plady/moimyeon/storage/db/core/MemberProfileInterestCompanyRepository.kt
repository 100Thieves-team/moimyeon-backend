package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberProfileInterestCompanyRepository : JpaRepository<MemberProfileInterestCompanyEntity, Long> {
    fun findByProfileIdAndDeletedAtIsNull(profileId: UUID): List<MemberProfileInterestCompanyEntity>
}
