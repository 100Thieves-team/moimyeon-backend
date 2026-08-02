package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.mock.MockApiProfile
import io.plady.moimyeon.core.api.controller.v1.request.CreateJobPostingRequest
import io.plady.moimyeon.core.api.controller.v1.request.JobPostingLinkMetadataRequest
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingLinkMetadataResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

// TODO(채용 공고): 링크 즉시 추가(OG 수집·파싱)는 BE-03(MOI-334)에서 실구현한다. 그 전까지 고정 메타/생성 결과를 반환한다.
// 회사·직무·공고 조회는 실데이터(BE-02B, MOI-326)로 옮겨졌고, 여기 남은 두 POST 만 모킹이다.
@MockApiProfile
@RestController
class MockJobPostingController {
    // POST /v1/job-postings/link-metadata — 목록에 없는 공고를 만들기 전, 링크의 OG 태그를 읽어 공고명 후보·미리보기를 돌려준다(§4.1).
    // 회사는 여기서 추출하지 않는다. 목은 링크와 무관하게 고정 메타를 반환하되 요청 url 을 출처로 반영한다.
    @PostMapping("/v1/job-postings/link-metadata")
    fun linkMetadata(
        @RequestBody request: JobPostingLinkMetadataRequest,
    ): ApiResponse<JobPostingLinkMetadataResponse> {
        request.validate()
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
        @RequestBody request: CreateJobPostingRequest,
    ): ApiResponse<JobPostingCreatedResponse> {
        request.validate()
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

    companion object {
        private const val MOCK_CREATED_POSTING_ID = 90101L
    }
}
