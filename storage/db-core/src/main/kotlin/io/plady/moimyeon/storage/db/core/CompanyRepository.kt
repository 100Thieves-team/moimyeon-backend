package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface CompanyRepository : JpaRepository<CompanyEntity, Long> {
    fun findTop20ByNameKrContainingAndVerifiedTrueAndDeletedAtIsNullOrderByNameKrAsc(query: String): List<CompanyEntity>

    fun findByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<CompanyEntity>

    fun countByIdInAndVerifiedTrueAndDeletedAtIsNull(ids: Collection<Long>): Long
}
