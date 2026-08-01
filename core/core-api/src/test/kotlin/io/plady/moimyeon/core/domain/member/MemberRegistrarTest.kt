package io.plady.moimyeon.core.domain.member

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.domain.profile.ProfileManager
import io.plady.moimyeon.core.domain.terms.TermsAgreementManager
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MemberRegistrarTest {
    private val memberRepository = mockk<MemberRepository>()
    private val termsAgreementManager = mockk<TermsAgreementManager>()
    private val profileManager = mockk<ProfileManager>()
    private val memberRegistrar = MemberRegistrar(memberRepository, termsAgreementManager, profileManager)

    private val provider = SocialLoginProvider.GOOGLE
    private val email = Email("user@example.com")
    private val nickname = Nickname("차분한 펭귄 12")
    private val registeredAt = LocalDateTime.of(2026, 8, 1, 12, 0)

    @Test
    fun `회원과 필수 약관 동의와 프로필을 하나의 가입 단위로 등록한다`() {
        // given
        val saved = slot<MemberEntity>()
        every {
            memberRepository.existsBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNotNull(
                provider,
                "sub-1",
            )
        } returns false
        every { memberRepository.saveAndFlush(capture(saved)) } answers { saved.captured }
        every { termsAgreementManager.agreeRequired(any(), registeredAt) } just Runs
        every { profileManager.createEmpty(any()) } returns UUID.randomUUID()

        // when
        val memberId = memberRegistrar.register(provider, "sub-1", email, nickname, registeredAt)

        // then
        val entity = saved.captured
        assertThat(memberId).isEqualTo(entity.id)
        assertThat(entity.email).isEqualTo("user@example.com")
        assertThat(entity.nickname).isEqualTo("차분한 펭귄 12")
        assertThat(entity.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(entity.lastLoginAt).isEqualTo(registeredAt)
        assertThat(entity.socialAccounts()).hasSize(1)
        val socialAccount = entity.socialAccounts().first()
        assertThat(socialAccount.provider).isEqualTo(provider)
        assertThat(socialAccount.providerId).isEqualTo("sub-1")
        assertThat(socialAccount.linkedEmail).isEqualTo("user@example.com")
        verify(exactly = 1) { termsAgreementManager.agreeRequired(memberId, registeredAt) }
        verify(exactly = 1) { profileManager.createEmpty(memberId) }
    }

    @Test
    fun `탈퇴 회원이 점유한 소셜 신원으로는 다시 가입할 수 없다`() {
        // given
        every {
            memberRepository.existsBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNotNull(
                provider,
                "sub-2",
            )
        } returns true

        // when & then
        assertThatThrownBy { memberRegistrar.register(provider, "sub-2", email, nickname, registeredAt) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_ALREADY_WITHDRAWN)
            }
        verify(exactly = 0) { memberRepository.saveAndFlush(any()) }
        verify(exactly = 0) { termsAgreementManager.agreeRequired(any(), any()) }
        verify(exactly = 0) { profileManager.createEmpty(any()) }
    }
}
