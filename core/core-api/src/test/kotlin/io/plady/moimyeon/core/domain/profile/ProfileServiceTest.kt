package io.plady.moimyeon.core.domain.profile

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.domain.catalog.CompanyFinder
import io.plady.moimyeon.core.domain.catalog.JobCatalogFinder
import io.plady.moimyeon.core.domain.catalog.RegionFinder
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberFinder
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
    private val nicknameGenerator = mockk<NicknameGenerator>()
    private val profileService = ProfileService(
        memberFinder,
        termsAgreementFinder,
        jobCatalogFinder,
        regionFinder,
        companyFinder,
        profileFinder,
        profileManager,
        nicknameGenerator,
    )

    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val member = Member.register(SocialLoginProvider.GOOGLE, "sub-1", Email("user@example.com"), now)
    private val memberId = member.id
    private val profile = MemberProfile(
        memberId = memberId,
        nickname = Nickname("차분한 펭귄 12"),
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
        every { profileFinder.isNicknameAvailable(profile.nickname) } returns true
    }

    private fun assertCreateFails(errorType: CoreErrorType) {
        assertThatThrownBy { profileService.create(profile) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }

    @Test
    fun `검증을 모두 통과하면 프로필을 저장하고 반환한다`() {
        givenCreatable()
        every { profileManager.append(profile) } returns profile

        val created = profileService.create(profile)

        assertThat(created).isEqualTo(profile)
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
    fun `닉네임이 중복이면 E1007 을 던진다`() {
        every { memberFinder.getById(memberId) } returns member
        givenValidCatalogRefs()
        every { termsAgreementFinder.hasAgreedAllRequiredActive(memberId) } returns true
        every { profileFinder.exists(memberId) } returns false
        every { profileFinder.isNicknameAvailable(profile.nickname) } returns false

        assertCreateFails(CoreErrorType.NICKNAME_DUPLICATED)
    }

    @Test
    fun `동시 요청으로 유니크 충돌이 나면 재조회로 구분해 닉네임 중복은 E1007 로 매핑한다`() {
        givenCreatable()
        every { profileManager.append(profile) } throws DataIntegrityViolationException("uk_member_profile_nickname")

        assertCreateFails(CoreErrorType.NICKNAME_DUPLICATED)
    }

    @Test
    fun `동시 요청으로 유니크 충돌이 났는데 내 프로필이 생겨 있으면 E1008 로 매핑한다`() {
        every { memberFinder.getById(memberId) } returns member
        givenValidCatalogRefs()
        every { termsAgreementFinder.hasAgreedAllRequiredActive(memberId) } returns true
        every { profileFinder.exists(memberId) } returns false andThen true
        every { profileFinder.isNicknameAvailable(profile.nickname) } returns true
        every { profileManager.append(profile) } throws DataIntegrityViolationException("pk")

        assertCreateFails(CoreErrorType.PROFILE_ALREADY_EXISTS)
    }

    @Test
    fun `동시 요청이 아닌 무결성 위반은 유니크 충돌로 오인하지 않고 전파한다`() {
        givenCreatable()
        every { profileManager.append(profile) } throws DataIntegrityViolationException("fk_member_profile_member")

        assertThatThrownBy { profileService.create(profile) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `수정은 자신을 제외한 닉네임 유일성을 확인하고 전체 교체한다`() {
        val updated = profile.copy(jobRoleId = 2L)
        every { memberFinder.getById(memberId) } returns member
        every { jobCatalogFinder.existsActiveRole(2L) } returns true
        every { regionFinder.existsActiveSigungu(2L) } returns true
        every { companyFinder.allActive(listOf(1L)) } returns true
        every { profileFinder.isNicknameAvailableFor(memberId, updated.nickname) } returns true
        every { profileManager.update(updated) } returns updated

        assertThat(profileService.update(updated)).isEqualTo(updated)
    }

    @Test
    fun `수정 시 다른 회원의 닉네임이면 E1007 을 던진다`() {
        every { memberFinder.getById(memberId) } returns member
        givenValidCatalogRefs()
        every { profileFinder.isNicknameAvailableFor(memberId, profile.nickname) } returns false

        assertThatThrownBy { profileService.update(profile) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.NICKNAME_DUPLICATED)
            }
    }

    @Test
    fun `수정 대상 프로필이 없으면 E1009 가 전파된다`() {
        every { memberFinder.getById(memberId) } returns member
        givenValidCatalogRefs()
        every { profileFinder.isNicknameAvailableFor(memberId, profile.nickname) } returns true
        every { profileManager.update(profile) } throws CoreException(CoreErrorType.PROFILE_NOT_FOUND)

        assertThatThrownBy { profileService.update(profile) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.PROFILE_NOT_FOUND)
            }
    }

    @Test
    fun `추천은 사용 가능한 후보가 나올 때까지 재생성한다`() {
        val taken = Nickname("집요한 수달 07")
        val available = Nickname("차분한 펭귄 12")
        every { nicknameGenerator.generate() } returns taken andThen available
        every { profileFinder.isNicknameAvailable(taken) } returns false
        every { profileFinder.isNicknameAvailable(available) } returns true

        assertThat(profileService.suggestNickname()).isEqualTo(available)
    }

    @Test
    fun `가용성 확인은 형식 위반이면 중복 여부와 무관하게 E1005 를 던진다`() {
        assertThatThrownBy { profileService.isNicknameAvailable("금지!문자@") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.INVALID_NICKNAME)
            }
    }
}
