package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.JobCatalogResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobRoleSearchResponse
import io.plady.moimyeon.core.api.controller.v1.response.RegionsResponse
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val QUERY_LENGTH_RANGE = 1..50

@RestController
class CatalogController(
    private val catalogService: CatalogService,
) {
    @GetMapping("/v1/job-roles")
    fun jobRoles(): ApiResponse<JobCatalogResponse> {
        return ApiResponse.success(JobCatalogResponse.from(catalogService.getJobCatalog()))
    }

    // 룸 생성 시 직무명으로 직무를 검색한다(§4.1). 직무는 공고와 독립한 평면 카탈로그이므로 공고 선택과 무관하게 고른다.
    @GetMapping("/v1/job-roles/search")
    fun searchJobRoles(
        @RequestParam query: String,
    ): ApiResponse<JobRoleSearchResponse> {
        if (query.isBlank() || query.length !in QUERY_LENGTH_RANGE) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }

        return ApiResponse.success(JobRoleSearchResponse.from(catalogService.searchJobRoles(query.trim())))
    }

    @GetMapping("/v1/regions")
    fun regions(): ApiResponse<RegionsResponse> {
        return ApiResponse.success(RegionsResponse.from(catalogService.getRegions()))
    }
}
