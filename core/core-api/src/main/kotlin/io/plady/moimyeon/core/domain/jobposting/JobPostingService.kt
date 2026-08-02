package io.plady.moimyeon.core.domain.jobposting

import org.springframework.stereotype.Service

@Service
class JobPostingService(
    private val jobPostingFinder: JobPostingFinder,
) {
    fun search(companyId: Long, query: String): List<JobPosting> = jobPostingFinder.search(companyId, query)
}
