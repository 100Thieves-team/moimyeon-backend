package io.plady.moimyeon.core.domain.jobposting

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.storage.db.core.JobPostingRepository
import org.springframework.stereotype.Component

@Component
class JobPostingValidator(
    private val jobPostingRepository: JobPostingRepository,
) {
    // 공고가 해당 회사에 속하고 신규 룸에 사용 가능한(활성·미폐기) 상태인지 검증한다.
    // 다른 회사 공고 조합이나 비활성/미존재 공고로는 룸을 만들 수 없다(MOI-328).
    fun validateSelectableInCompany(companyId: Long, jobPostingId: Long) {
        requireBusiness(
            jobPostingRepository.existsByIdAndCompanyIdAndIsOpenTrueAndDeletedAtIsNull(jobPostingId, companyId),
            CoreErrorType.JOB_POSTING_NOT_FOUND,
        )
    }
}
