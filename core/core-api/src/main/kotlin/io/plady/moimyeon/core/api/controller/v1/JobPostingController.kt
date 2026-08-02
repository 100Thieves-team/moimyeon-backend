package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.JobPostingsResponse
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val QUERY_MAX_LENGTH = 50

@RestController
class JobPostingController(
    private val jobPostingService: JobPostingService,
) {
    // GET /v1/companies/{companyId}/job-postings — 회사 선택 시 채워지는 공고 목록(§4.1).
    // 회사 기준 필터 + 공고명 검색(선택) + 활성만 + 최신순 최대 20건. 공고를 고르면 회사가 확정된다.
    @GetMapping("/v1/companies/{companyId}/job-postings")
    fun jobPostings(
        @PathVariable companyId: Long,
        @RequestParam(required = false, defaultValue = "") query: String,
    ): ApiResponse<JobPostingsResponse> {
        if (query.length > QUERY_MAX_LENGTH) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }

        return ApiResponse.success(JobPostingsResponse.from(jobPostingService.search(companyId, query.trim())))
    }
}
