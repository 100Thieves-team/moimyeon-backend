package io.plady.moimyeon.core.domain.jobposting

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.storage.db.core.JobPostingRepository
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class JobPostingValidator(
    private val jobPostingRepository: JobPostingRepository,
) {
    fun validateSelectableInCompany(companyId: Long, jobPostingId: Long) {
        log.debug { "job-posting.validator.validateSelectableInCompany companyId=$companyId jobPostingId=$jobPostingId" }
        requireBusiness(
            jobPostingRepository.existsByIdAndCompanyIdAndIsOpenTrueAndDeletedAtIsNull(jobPostingId, companyId),
            CoreErrorType.JOB_POSTING_NOT_FOUND,
        )
    }
}
