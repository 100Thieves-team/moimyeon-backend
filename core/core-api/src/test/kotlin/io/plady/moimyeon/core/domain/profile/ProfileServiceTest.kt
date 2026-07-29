package io.plady.moimyeon.core.domain.profile

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.domain.catalog.CompanyFinder
import io.plady.moimyeon.core.domain.catalog.JobCatalogFinder
import io.plady.moimyeon.core.domain.catalog.RegionFinder
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.member.Nickname
import io.plady.moimyeon.core.domain.terms.TermsAgreementFinder
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime

class ProfileServiceTest {
    private val memberFinder = mockk<MemberFinder>()
    private val termsAgreementFinder = mockk<TermsAgreementFinder>()
    private val jobCatalogFinder = mockk<JobCatalogFinder>()
    private val regionFinder = mockk<RegionFinder>()
    private val companyFinder = mockk<CompanyFinder>()
    private val profileFinder = mockk<ProfileFinder>()
    private val profileManager = mockk<ProfileManager>()
    private val profileService = ProfileService(
        memberFinder,
        termsAgreementFinder,
        jobCatalogFinder,
        regionFinder,
        companyFinder,
        profileFinder,
        profileManager,
    )

    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val member = Member.register(SocialLoginProvider.GOOGLE, "sub-1", Email("user@example.com"), Nickname("차분한 펭귄 12"), now)
    private val memberId = member.id
    private val content = ProfileContent(
        jobRoleId = 1L,
        bio = null,
        meetingPreference = null,
        sigunguId = 2L,
        interestCompanyIds = listOf(1L),
    )

    private fun givenValidCatalogRefs() {
        every { jobCatalogFinder.existsActiveRole(1L) } returns true
        every { regionFinder.existsActiveSigungu(2L) } returns true
        every { companyFinder.allActive(listOf(1L)) } returns true
    }

    private fun givenCreatable() {
        every { memberFinder.getById(memberId) } returns member
        givenValidCatalogRefs()
        every { termsAgreementFinder.hasAgreedAllRequiredActive(memberId) } returns true
        every { profileFinder.exists(memberId) } returns false
    }

    private fun assertCreateFails(errorType: CoreErrorType) {
        assertThatThrownBy { profileService.create(memberId, content) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }

    @Test
    fun `검증을 모두 통과하면 프로필을 저장하고 식별자를 반환한다`() {
        givenCreatable()
        every { profileManager.append(memberId, content) } returns memberId

        val created = profileService.create(memberId, content)

        assertThat(created).isEqualTo(memberId)
    }

    @Test
    fun `회원이 없거나 탈퇴했으면 E1006 을 던진다`() {
        every { memberFinder.getById(memberId) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        assertCreateFails(CoreErrorType.MEMBER_NOT_FOUND)
    }

    @Test
    fun `존재하지 않는 직무를 선택하면 E1301 을 던진다`() {
        every { memberFinder.getById(memberId) } returns member
        every { jobCatalogFinder.existsActiveRole(1L) } returns false

        assertCreateFails(CoreErrorType.JOB_ROLE_NOT_FOUND)
    }

    @Test
    fun `존재하지 않는 지역을 선택하면 E1302 를 던진다`() {
        every { memberFinder.getById(memberId) } returns member
        every { jobCatalogFinder.existsActiveRole(1L) } returns true
        every { regionFinder.existsActiveSigungu(2L) } returns false

        assertCreateFails(CoreErrorType.REGION_NOT_FOUND)
    }

    @Test
    fun `존재하지 않는 회사를 담으면 E1303 을 던진다`() {
        every { memberFinder.getById(memberId) } returns member
        every { jobCatalogFinder.existsActiveRole(1L) } returns true
        every { regionFinder.existsActiveSigungu(2L) } returns true
        every { companyFinder.allActive(listOf(1L)) } returns false

        assertCreateFails(CoreErrorType.COMPANY_NOT_FOUND)
    }

    @Test
    fun `필수 약관 미동의 상태면 E1201 을 던진다`() {
        every { memberFinder.getById(memberId) } returns member
        givenValidCatalogRefs()
        every { termsAgreementFinder.hasAgreedAllRequiredActive(memberId) } returns false

        assertCreateFails(CoreErrorType.TERMS_NOT_AGREED)
    }

    @Test
    fun `이미 프로필이 있으면 E1008 을 던진다`() {
        every { memberFinder.getById(memberId) } returns member
        givenValidCatalogRefs()
        every { termsAgreementFinder.hasAgreedAllRequiredActive(memberId) } returns true
        every { profileFinder.exists(memberId) } returns true

        assertCreateFails(CoreErrorType.PROFILE_ALREADY_EXISTS)
    }

    @Test
    fun `동시 요청으로 유니크 충돌이 났는데 내 프로필이 생겨 있으면 E1008 로 매핑한다`() {
        every { memberFinder.getById(memberId) } returns member
        givenValidCatalogRefs()
        every { termsAgreementFinder.hasAgreedAllRequiredActive(memberId) } returns true
        every { profileFinder.exists(memberId) } returns false andThen true
        every { profileManager.append(memberId, content) } throws DataIntegrityViolationException("pk")

        assertCreateFails(CoreErrorType.PROFILE_ALREADY_EXISTS)
    }

    @Test
    fun `동시 요청이 아닌 무결성 위반은 오인하지 않고 전파한다`() {
        givenCreatable()
        every { profileManager.append(memberId, content) } throws DataIntegrityViolationException("NULL not allowed for column")

        assertThatThrownBy { profileService.create(memberId, content) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `수정은 카탈로그 참조를 검증하고 전체 교체한다`() {
        val updatedContent = content.copy(jobRoleId = 2L)
        every { memberFinder.getById(memberId) } returns member
        every { jobCatalogFinder.existsActiveRole(2L) } returns true
        every { regionFinder.existsActiveSigungu(2L) } returns true
        every { companyFinder.allActive(listOf(1L)) } returns true
        every { profileManager.update(memberId, updatedContent) } returns memberId

        assertThat(profileService.update(memberId, updatedContent)).isEqualTo(memberId)
    }
}
