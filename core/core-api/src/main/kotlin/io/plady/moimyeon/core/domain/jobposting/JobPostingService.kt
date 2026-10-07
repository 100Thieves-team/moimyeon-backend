package io.plady.moimyeon.core.domain.jobposting

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.company.CompanyValidator
import org.springframework.stereotype.Service
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class JobPostingService(
    private val jobPostingFinder: JobPostingFinder,
    private val jobPostingManager: JobPostingManager,
    private val openGraphClient: OpenGraphClient,
    private val companyValidator: CompanyValidator,
    private val jobPostingSearchReader: JobPostingSearchReader,
) {
    fun search(companyId: Long, query: String): List<JobPosting> = jobPostingFinder.search(companyId, query)

    fun search(condition: JobPostingSearchCondition): List<JobPostingSearchItem> = jobPostingSearchReader.search(condition)

    fun getRefs(ids: Collection<Long>): List<JobPostingRef> = jobPostingFinder.getRefsByIds(ids)

    fun fetchLinkMetadata(url: String): LinkMetadata = openGraphClient.fetch(url)

    fun create(createdByMemberId: UUID, command: JobPostingCreationCommand): JobPosting {
        log.debug { "job-posting.create memberId=$createdByMemberId" }
        companyValidator.validateSelectable(listOf(command.companyId))
        val jobPostingId = jobPostingManager.create(command, createdByMemberId)
        return jobPostingFinder.getById(jobPostingId)
    }
}
