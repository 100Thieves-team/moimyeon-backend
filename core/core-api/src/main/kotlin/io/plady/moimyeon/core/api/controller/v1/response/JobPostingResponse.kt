package io.plady.moimyeon.core.api.controller.v1.response

// 회사별 채용 공고 목록(「룸 생성」 §4.1). 회사를 고르면 그 회사의 공고만 채워진다.
data class JobPostingsResponse(
    val jobPostings: List<JobPostingResponse>,
)

data class JobPostingResponse(
    val jobPostingId: Long,
    val companyId: Long,
    val postingName: String, // 프론트엔드 개발자 (결제플랫폼)
    // 공고에 매핑된 직무 힌트(선택 시 직무 셀렉트 자동 채움 용도). 목이라 실제 job_role 시드와 무관한 예시값.
    val jobRoleId: Long?,
    val jobRoleName: String?,
    val sourceUrl: String?, // 원본 공고 링크(og:url)
    val verified: Boolean, // 운영 검수 여부. 링크로 즉시 생성된 공고는 false(탐색 필터에서만 숨겨짐)
)

// 링크 메타데이터 조회(POST /v1/job-postings/link-metadata)의 결과. og:title 을 공고명 후보로, 나머지는 미리보기 카드에 쓴다.
// 회사는 이 응답에 포함하지 않는다 — 회사는 생성 요청에서 사용자가 목록으로 지정한다.
data class JobPostingLinkMetadataResponse(
    val postingName: String, // og:title (사용자가 확인·수정할 수 있는 제안값)
    val imageUrl: String?, // og:image
    val description: String?, // og:description
    val sourceUrl: String, // og:url (없으면 요청 url)
)

// 공고 즉시 생성(POST /v1/job-postings)의 결과. 승인 대기 없이 바로 룸 생성에 사용할 수 있다.
data class JobPostingCreatedResponse(
    val jobPostingId: Long,
    val companyId: Long,
    val postingName: String,
    val sourceUrl: String,
    val verified: Boolean, // false — 운영 사후 검수 대상
)
