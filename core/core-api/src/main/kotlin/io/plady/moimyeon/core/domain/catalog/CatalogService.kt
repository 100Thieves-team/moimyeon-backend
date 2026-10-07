package io.plady.moimyeon.core.domain.catalog

import org.springframework.stereotype.Service

@Service
class CatalogService(
    private val jobCatalogFinder: JobCatalogFinder,
    private val regionFinder: RegionFinder,
) {
    fun getJobCatalog(): List<JobGroup> = jobCatalogFinder.getJobCatalog()

    fun searchJobRoles(query: String): List<JobRoleSearchResult> = jobCatalogFinder.searchJobRoles(query)

    fun getRegions(): List<Sido> = regionFinder.getRegions()

    fun getJobRoles(ids: Collection<Long>): List<JobRole> = jobCatalogFinder.getJobRolesByIds(ids)

    fun getRegionLabels(sigunguIds: Collection<Long>): List<RegionLabel> = regionFinder.getRegionLabels(sigunguIds)
}
