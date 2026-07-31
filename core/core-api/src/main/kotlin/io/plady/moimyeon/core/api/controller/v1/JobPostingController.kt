package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.mock.MockApiProfile
import io.plady.moimyeon.core.api.controller.v1.request.CreateJobPostingRequest
import io.plady.moimyeon.core.api.controller.v1.request.JobPostingLinkMetadataRequest
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingLinkMetadataResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingsResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

// TODO(채용 공고): 모킹 응답(고정). 회사·직무 카탈로그는 실데이터지만 '공고' 카탈로그는 아직 시드/링크 생성이 미구현이라
// 회사별 고정 목록과 고정 생성 결과를 반환한다. 실제 구현 시 job_posting 카탈로그와 OG 태그 수집·파싱(J6)으로 교체한다.
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

    // POST /v1/job-postings/link-metadata — 목록에 없는 공고를 만들기 전, 링크의 OG 태그를 읽어 공고명 후보·미리보기를 돌려준다(§4.1).
    // 회사는 여기서 추출하지 않는다. 목은 링크와 무관하게 고정 메타를 반환하되 요청 url 을 출처로 반영한다.
    @PostMapping("/v1/job-postings/link-metadata")
    fun linkMetadata(
        @Valid @RequestBody request: JobPostingLinkMetadataRequest,
    ): ApiResponse<JobPostingLinkMetadataResponse> {
        return ApiResponse.success(
            JobPostingLinkMetadataResponse(
                postingName = "프론트엔드 개발자 (결제플랫폼)",
                imageUrl = "https://img.example.com/careers/fe-pay.png",
                description = "결제·정산 플랫폼 프론트엔드 개발자를 모집합니다.",
                sourceUrl = request.url,
            ),
        )
    }

    // POST /v1/job-postings — 링크로 공고를 즉시 생성한다(§4.1). 승인 대기 없이 verified=false 로 만들어져 바로 룸 생성에 쓸 수 있다.
    // 회사는 요청의 companyId(기존 카탈로그 회사)로 받고, 공고명은 사용자가 확정한 값을 사용한다.
    @PostMapping("/v1/job-postings")
    fun createJobPosting(
        @Valid @RequestBody request: CreateJobPostingRequest,
    ): ApiResponse<JobPostingCreatedResponse> {
        return ApiResponse.success(
            JobPostingCreatedResponse(
                jobPostingId = MOCK_CREATED_POSTING_ID,
                companyId = request.companyId,
                postingName = request.postingName,
                sourceUrl = request.url,
                verified = false,
            ),
        )
    }

    // 회사와 무관하게 고정 목록을 반환하되, 요청한 companyId 를 각 공고에 반영한다.
    private fun mockJobPostings(companyId: Long): JobPostingsResponse {
        return JobPostingsResponse(
            listOf(
                JobPostingResponse(
                    jobPostingId = 1L,
                    companyId = companyId,
                    postingName = "프론트엔드 개발자 (결제플랫폼)",
                    jobRoleId = 1L,
                    jobRoleName = "프론트엔드 개발",
                    sourceUrl = "https://dalbitpay.example.com/careers/fe-pay",
                    verified = true,
                ),
                JobPostingResponse(
                    jobPostingId = 2L,
                    companyId = companyId,
                    postingName = "백엔드 개발자 (정산)",
                    jobRoleId = 2L,
                    jobRoleName = "백엔드 개발",
                    sourceUrl = "https://dalbitpay.example.com/careers/be-settle",
                    verified = true,
                ),
                JobPostingResponse(
                    jobPostingId = 3L,
                    companyId = companyId,
                    postingName = "프로덕트 디자이너",
                    jobRoleId = 3L,
                    jobRoleName = "프로덕트 디자인",
                    sourceUrl = "https://dalbitpay.example.com/careers/product-designer",
                    verified = false,
                ),
            ),
        )
    }

    companion object {
        private const val MOCK_CREATED_POSTING_ID = 90101L
    }
}
