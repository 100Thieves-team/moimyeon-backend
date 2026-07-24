package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.SocialAuthService
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// 이슈 Verification: 약관 동의 → 닉네임 자동 생성 → 프로필 저장 → 완성 상태 전환 흐름 재현
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
    fun `가입(자동 동의)부터 프로필 저장·완성 상태 전환까지의 흐름이 동작한다`() {
        // given — 가입하면 필수 약관 동의가 기록된다 (시드 2건: SERVICE·PRIVACY v1.0)
        val memberId = signUp("google-sub-p1")
        assertThat(termsAgreementRepository.findByMemberId(memberId)).hasSize(2)
        assertThat(profileService.hasProfile(memberId)).isFalse()

        // when — 자동 생성 닉네임으로 프로필 저장
        val nickname = profileService.suggestNickname()
        val created = profileService.create(
            MemberProfile(memberId, nickname, jobTitle = "백엔드 개발", bio = null, meetingPreference = null, region = null),
        )

        // then — 완성 상태(프로필 존재)로 전환
        assertThat(created.nickname).isEqualTo(nickname)
        assertThat(profileService.getProfile(memberId).nickname).isEqualTo(nickname)
        assertThat(profileService.isNicknameAvailable(nickname.value)).isFalse()
    }

    @Test
    fun `이미 사용 중인 닉네임으로는 저장이 거부된다`() {
        // given
        val first = signUp("google-sub-p2")
        val second = signUp("google-sub-p3")
        val nickname = Nickname("차분한 펭귄 12")
        profileService.create(MemberProfile(first, nickname, null, null, null, null))

        // when & then
        assertThatThrownBy {
            profileService.create(MemberProfile(second, nickname, null, null, null, null))
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.NICKNAME_DUPLICATED)
        }
    }

    @Test
    fun `같은 회원은 프로필을 다시 작성할 수 없다`() {
        // given
        val memberId = signUp("google-sub-p4")
        profileService.create(MemberProfile(memberId, Nickname("명랑한 해달 33"), null, null, null, null))

        // when & then
        assertThatThrownBy {
            profileService.create(MemberProfile(memberId, Nickname("명랑한 해달 34"), null, null, null, null))
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.PROFILE_ALREADY_EXISTS)
        }
    }
}
