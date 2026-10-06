package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.jobposting.JobPostingSearchItem

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
