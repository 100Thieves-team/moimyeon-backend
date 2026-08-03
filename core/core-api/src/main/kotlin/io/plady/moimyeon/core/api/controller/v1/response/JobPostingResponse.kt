package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.jobposting.JobPosting
import io.plady.moimyeon.core.domain.jobposting.LinkMetadata

// 회사별 채용 공고 목록(「룸 생성」 §4.1). 회사를 고르면 그 회사의 공고만 채워진다.
data class JobPostingsResponse(
    val jobPostings: List<JobPostingResponse>,
) {
    companion object {
        fun from(jobPostings: List<JobPosting>): JobPostingsResponse {
            return JobPostingsResponse(
                jobPostings.map {
                    JobPostingResponse(
                        jobPostingId = it.id,
                        companyId = it.companyId,
                        postingName = it.postingName,
                        jobRoleId = it.jobRoleId,
                        jobRoleName = it.jobRoleName,
                        sourceUrl = it.sourceUrl,
                        verified = it.verified,
                    )
                },
            )
        }
    }
}

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
// companyId 는 요청에서 받은 값을 그대로 돌려준다 — "이 링크는 그 회사의 공고"라는 가정을 명시하고, 생성 요청에 그대로 잇는다.
data class JobPostingLinkMetadataResponse(
    val companyId: Long, // 요청에서 가정한 회사(그대로 echo, 생성 시 이 회사로 저장)
    // og:title(사용자가 확인·수정할 제안값). fetch 실패·OG 없음이면 null → 사용자가 공고명을 직접 입력한다.
    val postingName: String?,
    val imageUrl: String?, // og:image
    val description: String?, // og:description
    val sourceUrl: String, // og:url (없으면 요청 url)
) {
    companion object {
        fun from(companyId: Long, metadata: LinkMetadata): JobPostingLinkMetadataResponse = JobPostingLinkMetadataResponse(
            companyId = companyId,
            postingName = metadata.postingName,
            imageUrl = metadata.imageUrl,
            description = metadata.description,
            sourceUrl = metadata.sourceUrl,
        )
    }
}

// 공고 즉시 생성(POST /v1/job-postings)의 결과. 승인 대기 없이 바로 룸 생성에 사용할 수 있다.
data class JobPostingCreatedResponse(
    val jobPostingId: Long,
    val companyId: Long,
    val postingName: String,
    val sourceUrl: String,
    val verified: Boolean, // false — 운영 사후 검수 대상
) {
    companion object {
        fun from(jobPosting: JobPosting): JobPostingCreatedResponse = JobPostingCreatedResponse(
            jobPostingId = jobPosting.id,
            companyId = jobPosting.companyId,
            postingName = jobPosting.postingName,
            // 링크 생성 공고는 출처 url 이 항상 저장돼 있다(없으면 저장 로직 불변식 위반 → 버그).
            sourceUrl = requireNotNull(jobPosting.sourceUrl) { "링크 생성 공고에는 sourceUrl 이 있어야 합니다. jobPostingId=${jobPosting.id}" },
            verified = jobPosting.verified,
        )
    }
}
