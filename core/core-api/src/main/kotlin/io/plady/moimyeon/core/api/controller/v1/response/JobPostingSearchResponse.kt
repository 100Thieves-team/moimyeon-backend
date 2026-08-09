package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.jobposting.JobPostingSearchItem

// 회사·공고 통합 검색 결과. 회사명이 맞아도 공고명이 맞아도 결과는 `회사 | 공고명` 형태의 공고 행으로 통일된다.
// query 를 그대로 돌려주는 이유: 타이핑 중 호출이라 응답이 뒤섞여 도착할 수 있고,
// 클라이언트가 자기가 보낸 값과 대조해 늦게 온 이전 응답을 버려야 한다.
data class JobPostingSearchResponse(
    val query: String,
    val companies: List<CompanyResponse>,
    val jobPostings: List<JobPostingSearchItemResponse>,
) {
    companion object {
        fun of(
            query: String,
            companies: List<Company>,
            jobPostings: List<JobPostingSearchItem>,
            companyNames: Map<Long, Company>,
        ): JobPostingSearchResponse = JobPostingSearchResponse(
            query = query,
            companies = companies.map { CompanyResponse(it.id, it.name) },
            // 회사명을 채우지 못한 행은 뺀다. 고르면 회사가 확정되지 않아 선택지가 될 수 없다.
            jobPostings = jobPostings.mapNotNull { item ->
                companyNames[item.companyId]?.let { company ->
                    JobPostingSearchItemResponse(
                        jobPostingId = item.id,
                        company = CompanyResponse(company.id, company.name),
                        postingName = item.postingName,
                        jobRoleId = item.jobRoleId,
                        jobRoleName = item.jobRoleName,
                        sourceUrl = item.sourceUrl,
                        verified = item.verified,
                    )
                }
            },
        )

        fun empty(query: String): JobPostingSearchResponse = JobPostingSearchResponse(query, emptyList(), emptyList())
    }
}

data class JobPostingSearchItemResponse(
    val jobPostingId: Long,
    val company: CompanyResponse,
    val postingName: String,
    val jobRoleId: Long?,
    val jobRoleName: String?,
    val sourceUrl: String?,
    val verified: Boolean,
)
