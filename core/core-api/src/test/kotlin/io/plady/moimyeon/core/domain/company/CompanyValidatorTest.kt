package io.plady.moimyeon.core.domain.company

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.CompanyRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CompanyValidatorTest {
    private val companyRepository = mockk<CompanyRepository>()
    private val validator = CompanyValidator(companyRepository)

    @Test
    fun `중복을 제외한 회사가 모두 선택 가능하면 검증을 통과한다`() {
        every { companyRepository.countByIdInAndVerifiedTrueAndDeletedAtIsNull(setOf(1L, 2L)) } returns 2L

        validator.validateSelectable(listOf(1L, 1L, 2L))

        verify(exactly = 1) { companyRepository.countByIdInAndVerifiedTrueAndDeletedAtIsNull(setOf(1L, 2L)) }
    }

    @Test
    fun `미검증 또는 폐기된 회사가 하나라도 있으면 E1303 을 던진다`() {
        every { companyRepository.countByIdInAndVerifiedTrueAndDeletedAtIsNull(setOf(1L, 2L)) } returns 1L

        assertThatThrownBy { validator.validateSelectable(listOf(1L, 2L)) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.COMPANY_NOT_FOUND)
            }
    }
}
