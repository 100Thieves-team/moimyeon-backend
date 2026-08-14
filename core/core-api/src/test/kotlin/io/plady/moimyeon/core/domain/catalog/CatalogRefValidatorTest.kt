package io.plady.moimyeon.core.domain.catalog

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.JobRoleRepository
import io.plady.moimyeon.storage.db.core.SigunguRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CatalogRefValidatorTest {
    private val jobRoleRepository = mockk<JobRoleRepository>()
    private val sigunguRepository = mockk<SigunguRepository>()
    private val validator = CatalogRefValidator(jobRoleRepository, sigunguRepository)

    @Test
    fun `중복을 제외한 관심 직무가 모두 존재하면 선택할 수 있다`() {
        every { jobRoleRepository.countByIdInAndDeletedAtIsNull(setOf(1L, 2L)) } returns 2L

        validator.validateJobRoles(listOf(1L, 1L, 2L))

        verify(exactly = 1) { jobRoleRepository.countByIdInAndDeletedAtIsNull(setOf(1L, 2L)) }
    }

    @Test
    fun `존재하지 않는 관심 직무가 하나라도 있으면 E1301 을 던진다`() {
        every { jobRoleRepository.countByIdInAndDeletedAtIsNull(setOf(1L, 2L)) } returns 1L

        assertThatThrownBy { validator.validateJobRoles(listOf(1L, 2L)) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.JOB_ROLE_NOT_FOUND)
            }
    }

    @Test
    fun `유효한 시군구 참조를 검증할 수 있다`() {
        every { sigunguRepository.existsByIdAndDeletedAtIsNull(1L) } returns true

        validator.validateSigungu(1L)

        verify(exactly = 1) { sigunguRepository.existsByIdAndDeletedAtIsNull(1L) }
    }

    @Test
    fun `존재하지 않는 시군구를 선택하면 E1302 를 던진다`() {
        every { sigunguRepository.existsByIdAndDeletedAtIsNull(1L) } returns false

        assertThatThrownBy { validator.validateSigungu(1L) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.REGION_NOT_FOUND)
            }
    }
}
