package io.plady.moimyeon.core.domain.profile

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.plady.moimyeon.core.domain.catalog.CatalogRefValidator
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ProfileServiceTest {
    private val catalogRefValidator = mockk<CatalogRefValidator>()
    private val profileFinder = mockk<ProfileFinder>()
    private val profileManager = mockk<ProfileManager>()
    private val profileService = ProfileService(catalogRefValidator, profileFinder, profileManager)

    private val memberId = UUID.randomUUID()
    private val content = ProfileContent(
        bio = null,
        meetingPreference = null,
        sigunguId = 2L,
        interestJobRoleIds = listOf(1L),
        interestCompanyIds = listOf(1L),
    )

    private fun givenValidCatalogRefs(jobRoleIds: List<Long> = listOf(1L)) {
        every { catalogRefValidator.validateJobRoles(jobRoleIds) } just Runs
        every { catalogRefValidator.validateSigungu(2L) } just Runs
        every { catalogRefValidator.validateCompanies(listOf(1L)) } just Runs
    }

    private fun assertCreateFails(errorType: CoreErrorType) {
        assertThatThrownBy { profileService.create(memberId, content) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }

    @Test
    fun `카탈로그 참조를 검증한 뒤 저장하고 식별자를 반환한다`() {
        givenValidCatalogRefs()
        every { profileManager.append(memberId, content) } returns memberId

        assertThat(profileService.create(memberId, content)).isEqualTo(memberId)
    }

    @Test
    fun `존재하지 않는 관심 직무를 담으면 E1301 을 던진다`() {
        every { catalogRefValidator.validateJobRoles(listOf(1L)) } throws CoreException(CoreErrorType.JOB_ROLE_NOT_FOUND)

        assertCreateFails(CoreErrorType.JOB_ROLE_NOT_FOUND)
    }

    @Test
    fun `존재하지 않는 지역을 선택하면 E1302 를 던진다`() {
        every { catalogRefValidator.validateJobRoles(listOf(1L)) } just Runs
        every { catalogRefValidator.validateSigungu(2L) } throws CoreException(CoreErrorType.REGION_NOT_FOUND)

        assertCreateFails(CoreErrorType.REGION_NOT_FOUND)
    }

    @Test
    fun `존재하지 않는 회사를 담으면 E1303 을 던진다`() {
        every { catalogRefValidator.validateJobRoles(listOf(1L)) } just Runs
        every { catalogRefValidator.validateSigungu(2L) } just Runs
        every { catalogRefValidator.validateCompanies(listOf(1L)) } throws CoreException(CoreErrorType.COMPANY_NOT_FOUND)

        assertCreateFails(CoreErrorType.COMPANY_NOT_FOUND)
    }

    @Test
    fun `append 가 던진 도메인 에러는 그대로 전파한다`() {
        givenValidCatalogRefs()
        every { profileManager.append(memberId, content) } throws CoreException(CoreErrorType.PROFILE_ALREADY_EXISTS)

        assertCreateFails(CoreErrorType.PROFILE_ALREADY_EXISTS)
    }

    @Test
    fun `수정은 카탈로그 참조를 검증하고 전체 교체한다`() {
        val updatedContent = content.copy(interestJobRoleIds = listOf(1L, 2L))
        givenValidCatalogRefs(jobRoleIds = listOf(1L, 2L))
        every { profileManager.update(memberId, updatedContent) } returns memberId

        assertThat(profileService.update(memberId, updatedContent)).isEqualTo(memberId)
    }
}
