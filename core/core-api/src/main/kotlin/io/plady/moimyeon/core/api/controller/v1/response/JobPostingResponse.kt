package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.jobposting.JobPosting
import io.plady.moimyeon.core.domain.jobposting.LinkMetadata

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
    val postingName: String,
    val jobRoleId: Long?,
    val jobRoleName: String?,
    val sourceUrl: String?,
    val verified: Boolean,
)

data class JobPostingLinkMetadataResponse(
    val companyId: Long,
    val postingName: String?,
    val imageUrl: String?,
    val description: String?,
    val sourceUrl: String,
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

data class JobPostingCreatedResponse(
    val jobPostingId: Long,
    val companyId: Long,
    val postingName: String,
    val sourceUrl: String,
    val verified: Boolean,
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
