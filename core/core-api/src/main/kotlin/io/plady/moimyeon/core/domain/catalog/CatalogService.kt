package io.plady.moimyeon.core.domain.catalog

import org.springframework.stereotype.Service

@Service
class CatalogService(
    private val jobCatalogFinder: JobCatalogFinder,
    private val regionFinder: RegionFinder,
) {
    fun getJobCatalog(): List<JobGroup> = jobCatalogFinder.getJobCatalog()

    fun getRegions(): List<Sido> = regionFinder.getRegions()
}
