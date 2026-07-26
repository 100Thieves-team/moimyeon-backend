package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.mock.MockApiProfile
import io.plady.moimyeon.core.api.controller.v1.request.SubmitJobPostingLinkRequest
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingRequestResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingsResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

// TODO(채용 공고): 모킹 응답(고정). 회사·직무 카탈로그는 실데이터지만 '공고'는 카탈로그 도입이 미확정이라
// 회사별 고정 목록을 반환한다. 실제 구현 시 job_posting 카탈로그(테이블·시드)와 운영 추가 요청 처리로 교체한다.
@MockApiProfile
@RestController
class JobPostingController {
    // GET /v1/companies/{companyId}/job-postings — 회사 선택 시 채워지는 공고 목록.
    @GetMapping("/v1/companies/{companyId}/job-postings")
    fun jobPostings(
        @PathVariable companyId: Long,
    ): ApiResponse<JobPostingsResponse> {
        return ApiResponse.success(mockJobPostings(companyId))
    }

    // POST /v1/job-posting-requests — 목록에 없는 공고를 링크로 추가 요청. 목은 접수(RECEIVED)만 반환.
    @PostMapping("/v1/job-posting-requests")
    fun requestJobPosting(
        @Valid @RequestBody request: SubmitJobPostingLinkRequest,
    ): ApiResponse<JobPostingRequestResponse> {
        return ApiResponse.success(JobPostingRequestResponse(requestId = MOCK_REQUEST_ID, status = "RECEIVED"))
    }

    // 회사와 무관하게 고정 목록을 반환하되, 요청한 companyId 를 각 공고에 반영한다.
    private fun mockJobPostings(companyId: Long): JobPostingsResponse {
        return JobPostingsResponse(
            listOf(
                JobPostingResponse(
                    jobPostingId = 1L,
                    companyId = companyId,
                    title = "프론트엔드 개발자 (결제플랫폼)",
                    jobRoleId = 1L,
                    jobRoleName = "프론트엔드 개발",
                    sourceUrl = "https://dalbitpay.example.com/careers/fe-pay",
                    status = "OPEN",
                ),
                JobPostingResponse(
                    jobPostingId = 2L,
                    companyId = companyId,
                    title = "백엔드 개발자 (정산)",
                    jobRoleId = 2L,
                    jobRoleName = "백엔드 개발",
                    sourceUrl = "https://dalbitpay.example.com/careers/be-settle",
                    status = "OPEN",
                ),
                JobPostingResponse(
                    jobPostingId = 3L,
                    companyId = companyId,
                    title = "프로덕트 디자이너",
                    jobRoleId = 3L,
                    jobRoleName = "프로덕트 디자인",
                    sourceUrl = "https://dalbitpay.example.com/careers/product-designer",
                    status = "OPEN",
                ),
            ),
        )
    }

    companion object {
        private const val MOCK_REQUEST_ID = 5001L
    }
}
