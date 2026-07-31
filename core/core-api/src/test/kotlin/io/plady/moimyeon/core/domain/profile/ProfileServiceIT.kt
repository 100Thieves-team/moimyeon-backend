package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.SocialAuthService
import io.plady.moimyeon.core.enums.MeetingPreference
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// 이슈 Verification: 가입(약관 자동 동의 + 빈 프로필 생성) → 소개 저장 흐름 재현
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
    fun `가입하면 필수 약관 동의와 빈 프로필이 함께 만들어진다`() {
        // when — 시드 필수 약관 2건: SERVICE·PRIVACY v1.0
        val memberId = signUp("google-sub-p1")

        // then — 프로필은 회원당 항상 하나 존재하고, 미지정 필드는 null 이 아니라 값이다
        assertThat(termsAgreementRepository.findByMemberIdAndDeletedAtIsNull(memberId)).hasSize(2)
        val profile = profileService.getProfile(memberId)
        assertThat(profile.bio).isEmpty()
        assertThat(profile.meetingPreference).isEqualTo(MeetingPreference.UNSPECIFIED)
        assertThat(profile.sigunguId).isNull()
        assertThat(profile.interestJobRoleIds).isEmpty()
        assertThat(profile.interestCompanyIds).isEmpty()
    }

    @Test
    fun `프로필 수정은 전체 교체와 관심 직무·회사 교체를 지원한다`() {
        // given — 참조 id 는 seed.sql: jobRole 1(서버·백엔드)·2(프론트엔드), sigunguId 2(마포구), company 1·2·3
        val memberId = signUp("google-sub-p5")

        // when
        val updatedId = profileService.update(
            memberId,
            ProfileContent(
                bio = "자기소개",
                meetingPreference = MeetingPreference.BOTH,
                sigunguId = 2L,
                interestJobRoleIds = listOf(1L, 2L),
                interestCompanyIds = listOf(1L, 2L),
            ),
        )
        profileService.update(
            memberId,
            ProfileContent(
                bio = "자기소개",
                meetingPreference = MeetingPreference.BOTH,
                sigunguId = 2L,
                interestJobRoleIds = listOf(2L),
                interestCompanyIds = listOf(3L),
            ),
        )

        // then — 쓰기는 식별자만 반환하고, 다건은 통째로 교체된다
        assertThat(updatedId).isEqualTo(memberId)
        val found = profileService.getProfile(memberId)
        assertThat(found.bio).isEqualTo("자기소개")
        assertThat(found.interestJobRoleIds).containsExactly(2L)
        assertThat(found.interestCompanyIds).containsExactly(3L)
    }
}
