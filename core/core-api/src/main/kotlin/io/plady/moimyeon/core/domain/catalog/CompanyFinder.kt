package io.plady.moimyeon.core.domain.catalog

import io.plady.moimyeon.storage.db.core.CompanyRepository
import org.springframework.stereotype.Component

@Component
class CompanyFinder(
    private val companyRepository: CompanyRepository,
) {
    fun search(query: String): List<Company> {
        return companyRepository.findTop20ByNameKrContainingAndDeletedAtIsNullOrderByNameKrAsc(query)
            .map { Company(it.id, it.nameKr) }
    }

    fun findActiveByIds(companyIds: Collection<Long>): List<Company> {
        if (companyIds.isEmpty()) return emptyList()
        return companyRepository.findByIdInAndDeletedAtIsNull(companyIds).map { Company(it.id, it.nameKr) }
    }

    fun allActive(companyIds: Collection<Long>): Boolean {
        return findActiveByIds(companyIds).size == companyIds.toSet().size
    }
}
