package io.plady.moimyeon.core.domain.jobposting

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.JobPostingRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class JobPostingValidatorTest {
    private val jobPostingRepository = mockk<JobPostingRepository>()
    private val validator = JobPostingValidator(jobPostingRepository)

    @Test
    fun `공고가 회사에 속하고 활성이면 검증을 통과한다`() {
        every { jobPostingRepository.existsByIdAndCompanyIdAndIsOpenTrueAndDeletedAtIsNull(2029L, 1001L) } returns true

        assertThatCode { validator.validateSelectableInCompany(1001L, 2029L) }.doesNotThrowAnyException()
    }

    @Test
    fun `다른 회사 공고이거나 비활성·미존재면 E1304 를 던진다`() {
        every { jobPostingRepository.existsByIdAndCompanyIdAndIsOpenTrueAndDeletedAtIsNull(2029L, 1001L) } returns false

        assertThatThrownBy { validator.validateSelectableInCompany(1001L, 2029L) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.JOB_POSTING_NOT_FOUND)
            }
    }
}
