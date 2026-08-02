package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.CompanySearchResponse
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val QUERY_LENGTH_RANGE = 1..50

@RestController
class CompanyController(
    private val companyService: CompanyService,
) {
    @GetMapping("/v1/companies")
    fun search(
        @RequestParam query: String,
    ): ApiResponse<CompanySearchResponse> {
        if (query.isBlank() || query.length !in QUERY_LENGTH_RANGE) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }

        return ApiResponse.success(CompanySearchResponse.from(companyService.search(query)))
    }
}
