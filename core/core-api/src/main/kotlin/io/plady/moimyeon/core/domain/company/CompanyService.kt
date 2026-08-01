package io.plady.moimyeon.core.domain.company

import org.springframework.stereotype.Service

@Service
class CompanyService(
    private val companyFinder: CompanyFinder,
) {
    fun search(query: String): List<Company> = companyFinder.search(query)

    fun getCompanies(companyIds: Collection<Long>): List<Company> = companyFinder.getByIds(companyIds)
}
