package io.plady.moimyeon.core.api.controller.v1.response

// 회사별 채용 공고 목록(B·06). 회사를 고르면 그 회사의 공고만 채워진다.
data class JobPostingsResponse(
    val jobPostings: List<JobPostingResponse>,
)

data class JobPostingResponse(
    val jobPostingId: Long,
    val companyId: Long,
    val title: String, // 프론트엔드 개발자 (결제플랫폼)
    // 공고에 매핑된 직무 힌트(선택 시 직무 셀렉트 자동 채움 용도). 목이라 실제 job_role 시드와 무관한 예시값.
    val jobRoleId: Long?,
    val jobRoleName: String?,
    val sourceUrl: String?, // 원본 공고 링크
    val status: String, // OPEN
)

// 공고 링크 추가 요청(POST /v1/job-posting-requests)의 접수 결과.
data class JobPostingRequestResponse(
    val requestId: Long,
    val status: String, // RECEIVED
)
