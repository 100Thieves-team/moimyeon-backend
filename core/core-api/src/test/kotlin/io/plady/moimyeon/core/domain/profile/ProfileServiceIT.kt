package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.SocialAuthService
import io.plady.moimyeon.core.enums.MeetingPreference
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// 이슈 Verification: 가입(약관 자동 동의) → 소개 저장 → 완성 상태 전환 흐름 재현
@Transactional
class ProfileServiceIT(
    private val socialAuthService: SocialAuthService,
    private val profileService: ProfileService,
    private val termsAgreementRepository: TermsAgreementRepository,
) : ContextTest() {
    private fun signUp(providerId: String): UUID {
        return socialAuthService.authenticate(SocialLoginProvider.GOOGLE, providerId, Email("user@example.com"))
    }

    @Test
    fun `가입(자동 동의)부터 소개 저장·완성 상태 전환까지의 흐름이 동작한다`() {
        // given — 가입하면 필수 약관 동의가 기록된다 (시드 2건: SERVICE·PRIVACY v1.0)
        val memberId = signUp("google-sub-p1")
        assertThat(termsAgreementRepository.findByMemberIdAndDeletedAtIsNull(memberId)).hasSize(2)
        assertThat(profileService.hasProfile(memberId)).isFalse()

        // when — jobRoleId=1(서버·백엔드), sigunguId=1(강남구) — seed.sql 참조 데이터
        val createdId = profileService.create(
            memberId,
            ProfileContent(jobRoleId = 1L, bio = null, meetingPreference = null, sigunguId = 1L),
        )

        // then — 쓰기는 식별자만 반환하고, 상태는 조회로 확인한다 (완성 상태 = 프로필 존재)
        assertThat(createdId).isEqualTo(memberId)
        assertThat(profileService.hasProfile(memberId)).isTrue()
        val found = profileService.getProfile(memberId)
        assertThat(found.jobRoleId).isEqualTo(1L)
        assertThat(found.sigunguId).isEqualTo(1L)
    }

    @Test
    fun `프로필 수정은 전체 교체와 관심 회사 교체를 지원한다`() {
        // given
        val memberId = signUp("google-sub-p5")
        profileService.create(memberId, ProfileContent(null, null, null, null))

        // when — 참조 id 는 seed.sql: jobRoleId 1(서버·백엔드)·2(프론트엔드), sigunguId 2(마포구), company 1·2·3
        profileService.update(
            memberId,
            ProfileContent(
                jobRoleId = 1L,
                bio = "자기소개",
                meetingPreference = MeetingPreference.BOTH,
                sigunguId = 2L,
                interestCompanyIds = listOf(1L, 2L),
            ),
        )
        profileService.update(
            memberId,
            ProfileContent(
                jobRoleId = 2L,
                bio = "자기소개",
                meetingPreference = MeetingPreference.BOTH,
                sigunguId = 2L,
                interestCompanyIds = listOf(3L),
            ),
        )

        // then
        val found = profileService.getProfile(memberId)
        assertThat(found.jobRoleId).isEqualTo(2L)
        assertThat(found.interestCompanyIds).containsExactly(3L)
    }

    @Test
    fun `같은 회원은 프로필을 다시 작성할 수 없다`() {
        // given
        val memberId = signUp("google-sub-p4")
        profileService.create(memberId, ProfileContent(null, null, null, null))

        // when & then
        assertThatThrownBy {
            profileService.create(memberId, ProfileContent(jobRoleId = 1L, bio = null, meetingPreference = null, sigunguId = null))
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.PROFILE_ALREADY_EXISTS)
        }
    }
}
