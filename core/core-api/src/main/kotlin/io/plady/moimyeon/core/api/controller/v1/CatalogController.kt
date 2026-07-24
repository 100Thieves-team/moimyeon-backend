package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.CompanySearchResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobCatalogResponse
import io.plady.moimyeon.core.api.controller.v1.response.RegionsResponse
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.support.response.ApiResponse
import jakarta.validation.constraints.Size
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
class CatalogController(
    private val catalogService: CatalogService,
) {
    @GetMapping("/v1/job-roles")
    fun jobRoles(): ApiResponse<JobCatalogResponse> {
        return ApiResponse.success(JobCatalogResponse.from(catalogService.getJobGroups()))
    }

    @GetMapping("/v1/regions")
    fun regions(): ApiResponse<RegionsResponse> {
        return ApiResponse.success(RegionsResponse.from(catalogService.getRegions()))
    }

    @GetMapping("/v1/companies")
    fun searchCompanies(
        @RequestParam @Size(min = 1, max = 50) query: String,
    ): ApiResponse<CompanySearchResponse> {
        return ApiResponse.success(CompanySearchResponse.from(catalogService.searchCompanies(query)))
    }
}
