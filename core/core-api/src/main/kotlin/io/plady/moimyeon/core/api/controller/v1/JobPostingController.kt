package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CreateJobPostingRequest
import io.plady.moimyeon.core.api.controller.v1.request.JobPostingLinkMetadataRequest
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingLinkMetadataResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingSearchResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingsResponse
import io.plady.moimyeon.core.api.facade.JobPostingSearchFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val QUERY_MAX_LENGTH = 50
private val SEARCH_CACHE_MAX_AGE = java.time.Duration.ofSeconds(60)

@RestController
class JobPostingController(
    private val jobPostingService: JobPostingService,
    private val jobPostingSearchFacade: JobPostingSearchFacade,
) {
    // 개인화가 없는 응답이라 캐시를 허용하되, 링크로 방금 만든 공고가 곧 검색돼야 해서 짧게 잡는다.
    @GetMapping("/v1/job-postings/search")
    fun search(
        @RequestParam(required = false, defaultValue = "") query: String,
        @RequestParam(required = false) companyId: Long?,
    ): ResponseEntity<ApiResponse<JobPostingSearchResponse>> {
        if (query.length > QUERY_MAX_LENGTH) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(SEARCH_CACHE_MAX_AGE).cachePublic())
            .body(ApiResponse.success(jobPostingSearchFacade.search(query, companyId)))
    }

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

    @PostMapping("/v1/job-postings/link-metadata")
    fun linkMetadata(
        @RequestBody request: JobPostingLinkMetadataRequest,
    ): ApiResponse<JobPostingLinkMetadataResponse> {
        val url = request.toUrl()
        val metadata = jobPostingService.fetchLinkMetadata(url)
        return ApiResponse.success(JobPostingLinkMetadataResponse.from(request.companyId, metadata))
    }

    @PostMapping("/v1/job-postings")
    fun createJobPosting(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: CreateJobPostingRequest,
    ): ApiResponse<JobPostingCreatedResponse> {
        val created = jobPostingService.create(currentMember.id, request.toCommand())
        return ApiResponse.success(JobPostingCreatedResponse.from(created))
    }
}
