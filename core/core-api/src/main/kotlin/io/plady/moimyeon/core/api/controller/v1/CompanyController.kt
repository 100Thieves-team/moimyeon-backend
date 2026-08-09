package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.CompanySearchResponse
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val QUERY_MAX_LENGTH = 50

@RestController
class CompanyController(
    private val companyService: CompanyService,
) {
    // 타이핑 중 호출을 전제로 한다. 빈 입력·짧은 입력은 입력 중간 상태이지 에러가 아니라서
    // 400 대신 빈 배열 200 을 준다. 남용 방어인 최대 길이만 400 을 유지한다.
    @GetMapping("/v1/companies")
    fun search(
        @RequestParam(required = false, defaultValue = "") query: String,
    ): ApiResponse<CompanySearchResponse> {
        if (query.length > QUERY_MAX_LENGTH) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }

        return ApiResponse.success(CompanySearchResponse.from(companyService.search(query)))
    }
}
