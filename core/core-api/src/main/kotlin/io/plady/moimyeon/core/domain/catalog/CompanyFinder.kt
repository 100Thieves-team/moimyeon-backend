package io.plady.moimyeon.core.domain.catalog

import io.plady.moimyeon.storage.db.core.CompanyRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CompanyFinder(
    private val companyRepository: CompanyRepository,
) {
    @Transactional(readOnly = true)
    fun search(query: String): List<Company> {
        return companyRepository.findTop20ByNameKrContainingAndRetiredAtIsNullOrderByNameKrAsc(query)
            .map { Company(it.id, it.nameKr) }
    }

    @Transactional(readOnly = true)
    fun findActiveByIds(companyIds: Collection<Long>): List<Company> {
        if (companyIds.isEmpty()) return emptyList()
        return companyRepository.findByIdInAndRetiredAtIsNull(companyIds).map { Company(it.id, it.nameKr) }
    }

    @Transactional(readOnly = true)
    fun allActive(companyIds: Collection<Long>): Boolean {
        return findActiveByIds(companyIds).size == companyIds.toSet().size
    }
}
