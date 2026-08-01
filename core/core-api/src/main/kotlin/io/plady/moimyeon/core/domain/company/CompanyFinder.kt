package io.plady.moimyeon.core.domain.company

import io.plady.moimyeon.storage.db.core.CompanyRepository
import org.springframework.stereotype.Component

@Component
class CompanyFinder(
    private val companyRepository: CompanyRepository,
) {
    fun search(query: String): List<Company> {
        return companyRepository.findTop20ByNameKrContainingAndVerifiedTrueAndDeletedAtIsNullOrderByNameKrAsc(query)
            .map { Company(it.id, it.nameKr) }
    }

    fun getByIds(companyIds: Collection<Long>): List<Company> {
        if (companyIds.isEmpty()) return emptyList()
        return companyRepository.findByIdInAndDeletedAtIsNull(companyIds).map { Company(it.id, it.nameKr) }
    }
}
