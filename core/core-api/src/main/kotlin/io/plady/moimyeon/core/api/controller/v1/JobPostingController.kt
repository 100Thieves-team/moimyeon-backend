package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CreateJobPostingRequest
import io.plady.moimyeon.core.api.controller.v1.request.JobPostingLinkMetadataRequest
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingLinkMetadataResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingsResponse
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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

    // POST /v1/job-postings/link-metadata — 목록에 없는 공고를 만들기 전, 링크의 OG 태그를 서버가 읽어
    // 공고명 후보·미리보기를 돌려준다(§4.1). 사용자가 고른 companyId 를 함께 받아 "이 링크는 그 회사의 공고"라고
    // 일단 가정하고 그대로 응답에 echo 한다(회사 실존 검증은 생성에서). fetch 실패·OG 없음이면 postingName 이 null 로
    // 내려가고, 그때 사용자는 공고명을 직접 입력해 생성으로 넘어간다(깨진 링크 폴백).
    @PostMapping("/v1/job-postings/link-metadata")
    fun linkMetadata(
        @RequestBody request: JobPostingLinkMetadataRequest,
    ): ApiResponse<JobPostingLinkMetadataResponse> {
        val url = request.toUrl()
        val metadata = jobPostingService.fetchLinkMetadata(url)
        return ApiResponse.success(JobPostingLinkMetadataResponse.from(request.companyId, metadata))
    }

    // POST /v1/job-postings — 링크로 공고를 즉시 생성한다(§4.1). 승인 대기 없이 verified=false 로 만들어져
    // 바로 룸 생성에 쓸 수 있다. 회사는 요청의 companyId(기존 카탈로그 회사), 공고명은 사용자가 확정한 값을 쓰고,
    // 생성자는 인증 회원으로 기록한다(created_by_member_id). 같은 URL 재요청은 기존 공고를 재사용한다(멱등).
    @PostMapping("/v1/job-postings")
    fun createJobPosting(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: CreateJobPostingRequest,
    ): ApiResponse<JobPostingCreatedResponse> {
        val created = jobPostingService.create(currentMember.id, request.toCommand())
        return ApiResponse.success(JobPostingCreatedResponse.from(created))
    }
}
