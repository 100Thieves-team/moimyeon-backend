package io.plady.moimyeon.core.domain.catalog

import org.springframework.stereotype.Service

@Service
class CatalogService(
    private val jobCatalogFinder: JobCatalogFinder,
    private val regionFinder: RegionFinder,
    private val companyFinder: CompanyFinder,
) {
    fun getJobGroups(): List<JobGroup> = jobCatalogFinder.findActiveGroups()

    fun getRegions(): List<Sido> = regionFinder.findActiveSidos()

    fun searchCompanies(query: String): List<Company> = companyFinder.search(query)

    fun getCompanies(companyIds: Collection<Long>): List<Company> = companyFinder.findActiveByIds(companyIds)
}
