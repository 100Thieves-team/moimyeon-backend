package io.plady.moimyeon.core.domain.profile

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.plady.moimyeon.core.domain.catalog.CatalogRefValidator
import io.plady.moimyeon.core.domain.company.CompanyValidator
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ProfileServiceTest {
    private val catalogRefValidator = mockk<CatalogRefValidator>()
    private val companyValidator = mockk<CompanyValidator>()
    private val profileFinder = mockk<ProfileFinder>()
    private val profileManager = mockk<ProfileManager>()
    private val profileService = ProfileService(catalogRefValidator, companyValidator, profileFinder, profileManager)

    private val memberId = UUID.randomUUID()
    private val content = ProfileContent(
        bio = "자기소개",
        interestJobRoleIds = listOf(1L),
        interestCompanyIds = listOf(1L),
    )

    private fun givenValidCatalogRefs() {
        every { catalogRefValidator.validateJobRoles(listOf(1L)) } just Runs
        every { companyValidator.validateSelectable(listOf(1L)) } just Runs
    }

    private fun assertUpdateFails(errorType: CoreErrorType) {
        assertThatThrownBy { profileService.update(memberId, content) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }

    @Test
    fun `카탈로그 참조를 검증한 뒤 저장하고 식별자를 반환한다`() {
        givenValidCatalogRefs()
        every { profileManager.update(memberId, content) } returns memberId

        assertThat(profileService.update(memberId, content)).isEqualTo(memberId)
    }

    @Test
    fun `존재하지 않는 관심 직무를 담으면 E1301 을 던진다`() {
        every { catalogRefValidator.validateJobRoles(listOf(1L)) } throws CoreException(CoreErrorType.JOB_ROLE_NOT_FOUND)

        assertUpdateFails(CoreErrorType.JOB_ROLE_NOT_FOUND)
    }

    @Test
    fun `선택할 수 없는 회사를 담으면 E1303 을 던진다`() {
        every { catalogRefValidator.validateJobRoles(listOf(1L)) } just Runs
        every { companyValidator.validateSelectable(listOf(1L)) } throws CoreException(CoreErrorType.COMPANY_NOT_FOUND)

        assertUpdateFails(CoreErrorType.COMPANY_NOT_FOUND)
    }

    @Test
    fun `update 가 던진 도메인 에러는 그대로 전파한다`() {
        givenValidCatalogRefs()
        every { profileManager.update(memberId, content) } throws CoreException(CoreErrorType.PROFILE_NOT_FOUND)

        assertUpdateFails(CoreErrorType.PROFILE_NOT_FOUND)
    }

    @Test
    fun `여러 회원의 프로필을 한 번에 조회한다`() {
        val otherMemberId = UUID.randomUUID()
        val profiles = listOf(
            MemberProfile(memberId, "", listOf(1L), emptyList()),
            MemberProfile(otherMemberId, "", listOf(2L), emptyList()),
        )
        every { profileFinder.getAllByMemberIds(listOf(memberId, otherMemberId)) } returns profiles

        assertThat(profileService.getProfiles(listOf(memberId, otherMemberId)))
            .containsExactlyElementsOf(profiles)
    }
}
